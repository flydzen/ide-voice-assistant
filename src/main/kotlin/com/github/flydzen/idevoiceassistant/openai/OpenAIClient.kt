package com.github.flydzen.idevoiceassistant.openai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.flydzen.idevoiceassistant.commands.AssistantCommand
import com.intellij.openapi.diagnostic.thisLogger
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.Reasoning
import com.openai.models.ReasoningEffort
import com.openai.models.ResponsesModel
import com.openai.models.audio.AudioModel
import com.openai.models.audio.AudioResponseFormat
import com.openai.models.audio.transcriptions.TranscriptionCreateParams
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ToolChoiceOptions
import java.io.File
import java.time.Duration


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
    private val LOG = thisLogger()

    const val LITELLM_URL: String = "https://litellm.labs.jb.gg"
    private val LITELLM_API_KEY: String = System.getenv("LITELLM_API_KEY")
        ?: error("LITELLM_API_KEY environment variable is not set")

    private val PROMPT = """
You are an IDE voice command router (ru/en).
Map the user’s utterance to exactly one function call.

Rules:
- Call exactly one function. Output must be a function call only (no natural language).
- Choose the most specific function matching the intent.
- If intent is unclear, not an IDE command, or required parameters are missing, call idontknow(reason).
- Fill only parameters explicitly present in the utterance; do not invent or guess values.
- Preserve identifiers, paths, filenames, symbols, and casing verbatim.
- Apply and other synonyms means "approve", not an ideAction.
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
                .prompt(
                    "Transcribe developer voice commands for an IDE. " +
                            "Keep code terms as spoken. Plain text. " +
                            "If audio is noise, unclear, or not a command, return an empty string. " +
                            "Russian or English")
                .responseFormat(AudioResponseFormat.TEXT)
                .file(file.toPath())
                .temperature(0.0)
                .build()
        )
        val jsonString = response.asTranscription().text()
        LOG.info("raw recognized: $jsonString")
        val text = objectMapper.readValue<Map<String, Any>>(jsonString)["text"].toString()
        if (!text.any { it.isLetter() })
            return ""
        return text
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
}
