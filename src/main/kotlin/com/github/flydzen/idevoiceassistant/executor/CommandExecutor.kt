package com.github.flydzen.idevoiceassistant.executor

import com.github.flydzen.idevoiceassistant.commands.AssistantCommand
import com.github.flydzen.idevoiceassistant.commands.Command
import com.github.flydzen.idevoiceassistant.openai.CommandResult
import com.intellij.openapi.project.Project
import javax.swing.SwingUtilities.invokeLater

class CommandExecutor {
    fun execute(commands: List<Command>) {
        invokeLater {
            commands.forEach { it.process() }
        }
    }

    fun execute(project: Project, commandResults: List<CommandResult>) {
        val commands = commandResults.mapNotNull { AssistantCommand.toDomainCommand(project, it) }
        execute(commands)
    }
}
