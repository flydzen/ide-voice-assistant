package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils.editor
import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project

class StopCommand(private val project: Project) : Command() {
    override val toolName = "stop"
    override val description: String = "Stop current command. Also can be called by cancel, rollback and synonyms"
    override val parameters: List<Nothing> = emptyList()

    override fun process() {
        invokeLater {
            val editor = project.editor() ?: return@invokeLater
            AICodeGenActionsExecutor.stop(editor)
        }
        // TODO: stop other commands
    }

    override fun rollback() {}

    companion object {
        fun build(project: Project): StopCommand = StopCommand(project)
    }
}