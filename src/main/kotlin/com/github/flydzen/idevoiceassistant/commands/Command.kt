package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.CommandResult
import com.intellij.openapi.project.Project
import kotlin.reflect.full.companionObjectInstance

sealed class Command {
    abstract fun process()
    abstract fun rollback()

    companion object {
        val commands = Command::class.sealedSubclasses.mapNotNull { it.companionObjectInstance as? CommandMeta }

        fun getCommandByName(project: Project, prev: Command?, result: CommandResult): Command =
            when (val commandName = result.name.lowercase()) {
                EnterTextCommand.toolName -> EnterTextCommand.build(project, result.params)
                CodegenCommand.toolName -> CodegenCommand.build(project, result.params)
                FileNavigateCommand.toolName -> FileNavigateCommand.build(project, result.params)
                CreateFileCommand.toolName -> CreateFileCommand.build(project, result.params)
                CancelCommand.toolName -> CancelCommand.build(prev)
                ApproveCommand.toolName -> ApproveCommand.build(project)
                StopCommand.toolName -> StopCommand.build(project)
                IdeActionCommand.toolName -> IdeActionCommand.build(project, result.params)
                VimCommand.toolName -> VimCommand.build(project, result.params)
                NotificationCommand.toolName -> NotificationCommand.build(project, result.params)
                else -> throw IllegalArgumentException("Unknown command: $commandName")
            }
    }
}
