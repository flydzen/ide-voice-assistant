package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils.editor
import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project

class CodegenCommand(private val prompt: String, private val project: Project) : Command() {
    override val toolName = "generate"
    override val description: String =
        "Generate code in current file from only natural language prompt. Use it always when generating code except raw input"
    override val parameters: List<Parameter> = listOf(
        Parameter("prompt", "string", "test-based prompt for code generation. No code. Just english text.")
    )
    override val build: (project: Project, previousCommand: Command?, params: Map<String, Any>) -> Command? = { project, _, params ->
        val prompt = params["prompt"] as String
        CodegenCommand(prompt, project)
    }

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
