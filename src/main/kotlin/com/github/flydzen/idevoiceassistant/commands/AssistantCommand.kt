package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.CommandResult
import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.project.Project

enum class AssistantCommand(
    val toolName: String,
    val description: String,
    val parameters: List<Parameter>,
    val build: (project: Project, previousCommand: Command?, params: Map<String, Any>) -> Command?
) {
    INSERT(
        toolName = "insert",
        description = "Insert text at the cursor",
        parameters = listOf(
            Parameter("text", "string", "Text to insert")
        ),
        build = { project, _, params ->
            val text = params["text"] as String
            EnterTextCommand(project, text)
        }
    ),

    GENERATE(
        toolName = "generate",
        description = "Generate code in current file from only natural language prompt. Use it always when generating code except raw input",
        parameters = listOf(
            Parameter("prompt", "string", "test-based prompt for code generation. No code. Just english text.")
        ),
        build = { project, _, params ->
            val prompt = params["prompt"] as String
            CodegenCommand(project, prompt)
        }
    ),

    EDITOR_NAVIGATE(
        toolName = "fileNavigate",
        description = "Open file in editor",
        parameters = listOf(
            Parameter("fileName", "string", "File name to open (e.g., MyClass.kt)")
        ),
        build = { project, _, params ->
            val fileName = params["fileName"] as String
            FileNavigateCommand(project, fileName)
        }
    ),

    CREATE_FILE(
        toolName = "createFile",
        description = "Create new file",
        parameters = listOf(
            Parameter("path", "string", "File name or file path to create. (e.g., MyClass.kt or src/main/kotlin/MyClass.kt)")
        ),
        build = { project, _, params ->
            val path = params["path"] as String
            CreateFileCommand(project, path)
        }
    ),

    CANCEL(
        toolName = "cancel",
        description = "Cancel current command",
        parameters = emptyList(),
        build = { _, prev, _ ->
            CancelCommand(prev)
        }
    ),

    APPROVE(
        toolName = "approve",
        description = "Approve, changes. Any synonyms of approve, approve, accept should trigger that command",
        parameters = emptyList(),
        build = { project, _, _ ->
            ApproveCommand(project)
        }
    ),

    STOP(
        toolName = "stop",
        description = "Stop current command. Also can be called by cancel, rollback and synonyms",
        parameters = emptyList(),
        build = { project, _, _ ->
            StopCommand(project)
        }
    ),

    IDE_ACTION(
        toolName = "ideAction",
        description = "" +
                "Run Intellij IDEA action via `ActionManager.getInstance().getAction`. " +
                "Use it only if you know exactly action name.",
        parameters = listOf(
            Parameter("actionId", "string", "actionId of Intellij IDEA action (e.g., ReformatCode, Kotlin.NewFile)")
        ),
        build = { project, _, params ->
            val action = params["actionId"] as String
            IdeActionCommand(project, action)
        }
    ),

    VIM_ACTION(
        toolName = "vimCommand",
        description = "Execute Vim command. Use it only if you know exactly command.",
        parameters = listOf(Parameter("command", "string", "Vim command to be executed. If it is ex-command, start with `:`. For example, `:2`")),
        build = { project, _, params ->
            val command = params["command"] as String
            VimCommand(project, command)
        }
    ),

    IDONTKNOW(
        toolName = "idontknow",
        description = "If you don't know what to do, intent is unclear, or user must provide more information, " +
                "call idontknow(reason, research=False). " +
                "If the query needs deeper reasoning/research, " +
                "call idontknow(reason, research=True) to escalate to a heavier model.",
        parameters = listOf(
            Parameter("reason", "string", "The reason you don't know"),
            Parameter("research", "boolean", "Whether to redirect question to more powerfull model. Don't use if you need some information from user"),
        ),
        build = { project, _, params ->
            NotificationCommand(project, params["reason"] as String)
        }
    );

    companion object {
        private val byToolName = entries.associateBy { it.toolName.lowercase() }

        private fun fromResult(result: CommandResult): AssistantCommand? =
            byToolName[result.name.lowercase()]

        fun toDomainCommand(project: Project, prev: Command?, result: CommandResult): Command? =
            fromResult(result)?.build?.let { it(project, prev,result.params) }
    }
}