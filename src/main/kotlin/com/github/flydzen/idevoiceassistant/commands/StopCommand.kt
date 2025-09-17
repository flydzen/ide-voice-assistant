package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils.editor
import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project

class StopCommand(val project: Project) : Command() {
    override fun process() {
        invokeLater {
            val editor = project.editor() ?: return@invokeLater
            AICodeGenActionsExecutor.stop(editor)
        }
        // TODO: stop other commands
    }

    override fun rollback() {}
}