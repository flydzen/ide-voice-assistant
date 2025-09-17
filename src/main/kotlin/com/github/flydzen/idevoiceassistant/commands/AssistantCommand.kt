package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.CommandResult
import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.project.Project

enum class AssistantCommand(
    val toolName: String,
    val description: String,
    val parameters: List<Parameter>,
    val build: (project: Project, params: Map<String, Any>) -> Command?
) {
    INSERT(
        toolName = "insert",
        description = "Insert text at the cursor",
        parameters = listOf(
            Parameter("text", "string", "Text to insert")
        ),
        build = { project, params ->
            val text = params["text"] as String
            Command.EnterText(text, project)
        }
    ),

    GENERATE(
        toolName = "generate",
        description = "Generate code/content from instructions",
        parameters = listOf(
            Parameter("prompt", "string", "Prompt for code generation")
        ),
        build = { project, params ->
            val prompt = params["prompt"] as String
            Command.Codegen(prompt, project)
        }
    ),

    EDITOR_NAVIGATE(
        toolName = "editorNavigate",
        description = "Open file in editor",
        parameters = listOf(
            Parameter("fileName", "string", "File name to open (e.g., MyClass.kt)")
        ),
        build = { project, params ->
            val fileName = params["fileName"] as String
            Command.FileNavigate(fileName, project)
        }
    ),

    CANCEL(
        toolName = "cancel",
        description = "Cancel current command",
        parameters = emptyList(),
        build = { project, _ ->
            Command.Cancel(project)
        }
    ),

    APPROVE(
        toolName = "approve",
        description = "Approve changes",
        parameters = emptyList(),
        build = { project, _ ->
            Command.Approve(project)
        }
    ),

    STOP(
        toolName = "stop",
        description = "Stop current command",
        parameters = emptyList(),
        build = { project, _ ->
            Command.Stop(project)
        }
    ),

    IDONTKNOW(
        toolName = "idontknow",
        description = "If you don't know what to do",
        parameters = emptyList(),
        build = { project, _ ->
            Command.NotificationCommand(project)
        }
    );

    companion object {
        private val byToolName = entries.associateBy { it.toolName.lowercase() }

        private fun fromResult(result: CommandResult): AssistantCommand? =
            byToolName[result.name.lowercase()]

        fun toDomainCommand(project: Project, result: CommandResult): Command? =
            fromResult(result)?.build?.let { it(project, result.params) }
    }
}