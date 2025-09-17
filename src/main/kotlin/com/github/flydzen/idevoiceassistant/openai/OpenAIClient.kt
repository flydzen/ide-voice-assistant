package com.github.flydzen.idevoiceassistant.openai

import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.models.*
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ToolChoiceOptions
import java.time.Duration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.flydzen.idevoiceassistant.commands.AssistantCommand
import com.github.flydzen.idevoiceassistant.commands.Command
import com.intellij.openapi.project.Project
import com.openai.models.audio.AudioModel
import com.openai.models.audio.AudioResponseFormat
import com.openai.models.audio.transcriptions.TranscriptionCreateParams
import com.openai.models.audio.transcriptions.TranscriptionInclude
import fleet.kernel.transactor
import java.io.File


data class Parameter(
    val name: String,
    val type: String = "string",
    val description: String = "",
) {
    fun toMap() = mapOf(
        "type" to type,
        "description" to description
    )
}

data class CommandResult(
    val name: String,
    val params: Map<String, Any>
)

object OpenAIClient {
    const val LITELLM_URL: String = "https://litellm.labs.jb.gg"
    private val LITELLM_API_KEY: String = System.getenv("LITELLM_API_KEY")
        ?: error("LITELLM_API_KEY environment variable is not set")

    private val PROMPT = """
        You control an IntelliJ IDEA assistant. Your task is to map the user's natural-language request to exactly one function call.
        Important rules:
        - You must call exactly one function. Do not output any natural-language text.
        - Choose the most specific function that satisfies the intent.
    """.trimIndent()
    private val objectMapper = jacksonObjectMapper()

    private val client: OpenAIClient by lazy {
        OpenAIOkHttpClient.builder()
            .baseUrl(LITELLM_URL)
            .apiKey(LITELLM_API_KEY)
            .timeout(Duration.ofSeconds(10))
            .build()
    }

    private fun function(name: String, description: String, parameters: List<Parameter>) =
        FunctionTool.builder()
            .name(name)
            .description(description)
            .parameters(
                FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(
                        parameters.associate { it.name to it.toMap() }
                        )
                    )
                    .putAdditionalProperty("required", JsonValue.from(parameters.map { it.name }.toList()))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build()
            )
            .strict(true)
            .build()

    fun speech2Text(file: File): String {
        val response = client.audio().transcriptions().create(
            TranscriptionCreateParams.builder()
                .model(AudioModel.GPT_4O_TRANSCRIBE)
                .prompt("This is a user input to control Code Editor.")
                .responseFormat(AudioResponseFormat.TEXT)
                .file(file.toPath())
                .build()
        )
        val jsonString = response.asTranscription().text()
        if (jsonString == "context:\\n")
            return ""
        return objectMapper.readValue<Map<String, Any>>(jsonString)["text"].toString()
    }

    fun textToCommand(text: String): List<CommandResult> {
        val inputs = mutableListOf(
            ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                    .role(ResponseInputItem.Message.Role.SYSTEM)
                    .addInputTextContent(PROMPT)
                    .build()
            ),
            ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                    .role(ResponseInputItem.Message.Role.USER)
                    .addInputTextContent(text)
                    .build()
            ),
        )

        val builder = ResponseCreateParams.builder()
            .model(ResponsesModel.ofString("openai/gpt-5-nano"))
            .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
            .input(ResponseCreateParams.Input.ofResponse(inputs))
        AssistantCommand.entries.forEach { cmd ->
            builder.addTool(
                function(
                    name = cmd.toolName,
                    description = cmd.description,
                    parameters = cmd.parameters
                )
            )
        }
        val params = builder
            .toolChoice(ToolChoiceOptions.REQUIRED)
            .build()
        val response = client.responses().create(params)
        val result = response.output().filter { it.isFunctionCall() }.map { it.asFunctionCall() }
        return result.map {
            val argumentsJson = it.arguments()
            val argumentsMap: Map<String, Any> = if (argumentsJson.isBlank()) {
                emptyMap()
            } else {
                objectMapper.readValue(argumentsJson)
            }
            CommandResult(it.name(), params = argumentsMap)
        }
    }

    fun textToCommands(project: Project, text: String): List<Command> {
        val results = textToCommand(text)
        return results.mapNotNull { AssistantCommand.toDomainCommand(project, it) }
    }
}
