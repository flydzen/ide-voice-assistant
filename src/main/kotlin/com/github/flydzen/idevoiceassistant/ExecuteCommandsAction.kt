package com.github.flydzen.idevoiceassistant

import com.github.flydzen.idevoiceassistant.commands.Command
import com.github.flydzen.idevoiceassistant.commands.EnterTextCommand
import com.github.flydzen.idevoiceassistant.commands.FileNavigateCommand
import com.github.flydzen.idevoiceassistant.executor.CommandExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class ExecuteCommandsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Example: Create some sample commands
        val commands = createSampleCommands(project)

        // Execute commands using your executor
        project.service<CommandExecutor>().execute(commands)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        // Enable action only when project is available
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun createSampleCommands(project: Project): List<Command> {
        // This is where you would get your commands from wherever they come from
        // For now, returning an example list
        return listOf(
            FileNavigateCommand("Main.kt", project),
            EnterTextCommand("Hello from Voice Assistant!", project),
        )
    }
}