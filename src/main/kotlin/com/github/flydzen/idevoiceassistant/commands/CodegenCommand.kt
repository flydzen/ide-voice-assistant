package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils.editor
import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project

class CodegenCommand(private val prompt: String, private val project: Project) : Command() {
    override fun process() {
        invokeLater {
            val editor = project.editor() ?: return@invokeLater
            AICodeGenActionsExecutor.generateCode(prompt, editor)
        }
    }

    override fun rollback() {
        invokeLater {
            val editor = project.editor() ?: return@invokeLater
            AICodeGenActionsExecutor.discard(editor)
        }
    }

    override fun toString(): String = "Codegen(prompt='$prompt')"
}
