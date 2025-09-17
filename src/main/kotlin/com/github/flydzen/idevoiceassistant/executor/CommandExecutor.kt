package com.github.flydzen.idevoiceassistant.executor

import com.github.flydzen.idevoiceassistant.commands.AssistantCommand
import com.github.flydzen.idevoiceassistant.commands.Command
import com.github.flydzen.idevoiceassistant.openai.CommandResult
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import javax.swing.SwingUtilities.invokeLater

@Service(Service.Level.PROJECT)
class CommandExecutor {
    private var previousCommand: Command? = null
    fun execute(commands: List<Command>) {
        invokeLater {
            commands.forEach { it.process() }
        }
    }

    fun execute(project: Project, commandResults: List<CommandResult>) {
        val commands = commandResults.runningFold(previousCommand) { previousCommand, currentResult ->
            AssistantCommand.toDomainCommand(project, previousCommand, currentResult)
        }
            .drop(1)
            .filterNotNull()
        previousCommand = commands.lastOrNull()
        execute(commands)
    }
}
