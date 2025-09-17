package com.github.flydzen.idevoiceassistant.openai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.flydzen.idevoiceassistant.Config
import com.github.flydzen.idevoiceassistant.Utils.language
import com.github.flydzen.idevoiceassistant.commands.AssistantCommand
import com.github.flydzen.idevoiceassistant.services.Stage
import com.github.flydzen.idevoiceassistant.services.StageService
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
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


enum class GPTModels(val modelName: String) {
    GPT5_NANO("openai/gpt-5-nano"),     // works slow and good
    GPT5("openai/gpt-5"),               // for super heavy tasks
    GPT4O_MINI("openai/gpt-4o-mini"),   // works fast and good
}


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
    val params: Map<String, Any>,
)

object OpenAIClient {
    private val LOG = thisLogger()

    private const val LITELLM_URL: String = "https://litellm.labs.jb.gg"
    private val LITELLM_API_KEY: String = System.getenv("LITELLM_API_KEY")
        .takeIf { !it.isNullOrBlank() }
        ?: error("LITELLM_API_KEY environment variable is not set")

    private fun getPromptBase(language: Language?) = """
You are an IDE voice command router (ru/en). Map the user’s utterance to exactly one function call.

Rules:
- Output must be a function call only (no natural language).
- Choose the most specific function matching the intent.
- Do not invent or guess argument values. Preserve identifiers, paths, filenames, symbols, and casing verbatim.
- If intent is unclear, or user must provide more information, call idontknow(reason, research=False)
- If the query needs deeper reasoning/research, call idontknow(reason, research=True) to escalate to a heavier model.
- Apply and other synonyms means "approve", not an ideAction.
- You can a little guess what user prefer to do
${if (language != null) "- You must use ${language.displayName} language" else ""}
""".trimIndent()
    private fun getPromptLight(language: Language?) = getPromptBase(language) + """
- If you need to call Intellij Action, do idontknow(research=True).        
""".trimIndent()

    private val objectMapper = jacksonObjectMapper()

    private val client: OpenAIClient by lazy {
        OpenAIOkHttpClient.builder()
            .baseUrl(LITELLM_URL)
            .apiKey(LITELLM_API_KEY)
            .timeout(Duration.ofSeconds(60))
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

    fun textToCommand(project: Project, text: String, model: GPTModels = GPTModels.GPT4O_MINI): List<CommandResult> {
        val inputs = getInputs(project, text, model)
        val params = getParams(inputs, model)
        val response = client.responses().create(params)
        LOG.info("TTC response: $response")
        val commands = response.output()
            .asSequence()
            .mapNotNull { it.takeIf { it.isFunctionCall() }?.asFunctionCall() }
            .map { functionCall ->
                val params: Map<String, Any> = functionCall.arguments()
                    .takeIf { it.isNotBlank() }
                    ?.let { arguments ->
                        runCatching {
                            objectMapper.readValue<Map<String, Any>>(arguments)
                        }.getOrElse { emptyMap() }
                    }
                    ?: emptyMap()
                CommandResult(functionCall.name(), params = params)
            }
            .toList()
        if (model != GPTModels.GPT5 && commands.any { it.name == "idontknow" && it.params["research"] == true }){
            LOG.info("Escalate to GPT5 for: $text")
            project.service<StageService>().setStage(Stage.Investigating)
            return textToCommand(project, text, GPTModels.GPT5)
        }
        return commands
    }

    private fun getInputs(project: Project, text: String, model: GPTModels): List<ResponseInputItem> {
        val language = project.language()
        val inputs = mutableListOf<ResponseInputItem>()
        inputs.add(
            ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                    .role(ResponseInputItem.Message.Role.SYSTEM)
                    .addInputTextContent(
                        if (model == GPTModels.GPT4O_MINI) getPromptLight(language) else getPromptBase(language)
                    ).build()
            )
        )

        val previousCommands = getPreviousNCommands(project, Config.AMOUNT_LAST_COMMANDS_TO_REMEMBER)
        inputs.addAll(previousCommands)
        inputs.add(text.toResponseUserInputItem())
        return inputs
    }

    @Suppress("SameParameterValue")
    private fun getPreviousNCommands(project: Project, n: Int): List<ResponseInputItem> {
        val previousCommands = project.service<CommandHistoryStorage>().getLastNCommands(n)
        val responseInputItems = previousCommands.map { cmd -> cmd.toResponseUserInputItem() }
        return responseInputItems
    }

    private fun String.toResponseUserInputItem() =
        ResponseInputItem.ofMessage(
            ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addInputTextContent(this)
                .build()
        )

    private fun getParams(inputs: List<ResponseInputItem>, model: GPTModels): ResponseCreateParams {
        val builder = ResponseCreateParams.builder()
            .model(ResponsesModel.ofString(model.modelName))
            .input(ResponseCreateParams.Input.ofResponse(inputs))
            .toolChoice(ToolChoiceOptions.REQUIRED)
        AssistantCommand.entries.forEach { cmd ->
            builder.addTool(
                function(
                    name = cmd.toolName,
                    description = cmd.description,
                    parameters = cmd.parameters
                )
            )
        }
        return builder.build()
    }
}
