package com.github.flydzen.idevoiceassistant.openai

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.models.*
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseOutputItem
import com.openai.models.responses.Tool
import com.openai.models.responses.ToolChoiceOptions
import io.ktor.http.parameters
import java.time.Duration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue


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

data class Result(
    val name: String,
    val params: Map<String, Any>
)

object OpenAIResponsesClient {
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


    fun createOpenAIClient(): OpenAIClient {
        val client = OpenAIOkHttpClient.builder()
            .baseUrl(LITELLM_URL)
            .apiKey(LITELLM_API_KEY)
            .timeout(Duration.ofSeconds(60))
            .build()
        return client
    }

    fun function(name: String, description: String, parameters: List<Parameter>) =
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

    fun run(text: String): List<Result> {
        val client: OpenAIClient = createOpenAIClient()

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

        val params = ResponseCreateParams.builder()
            .model(ResponsesModel.ofString("openai/gpt-5-nano"))
            .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
            .input(ResponseCreateParams.Input.ofResponse(inputs))
            .addTool(
                function(
                    name="insert",
                    description = "Insert text at the cursor",
                    parameters = listOf(
                        Parameter("text", "string", "Text to insert")
                    )
                )
            )
            .addTool(
                function(
                    name="generate",
                    description = "Generate code/content from instructions",
                    parameters = listOf(
                        Parameter("prompt", "string", "Prompt for code generation")
                    ),
                )
            )
            .addTool(
                function(
                    name="idontknow",
                    description = "if you don't know what to do",
                    parameters = listOf(),
                )
            )
            .toolChoice(ToolChoiceOptions.REQUIRED)
            .build()
        println(params)
        val response = client.responses().create(params)
        val result = response.output().filter { it.isFunctionCall() }.map { it.asFunctionCall() }
        return result.map {
            val argumentsJson = it.arguments()
            val argumentsMap: Map<String, Any> = if (argumentsJson.isBlank()) {
                emptyMap()
            } else {
                objectMapper.readValue(argumentsJson)
            }
            Result(it.name(), params = argumentsMap)
        }
    }
}
