package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils.editor
import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project

class CodegenCommand(private val project: Project, private val prompt: String) : Command() {

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

    companion object : CommandMeta {
        override val toolName = "generate"
        override val description: String =
            "Generate code in current file from only natural language prompt. Use it always when generating code except raw input"
        override val parameters: List<Parameter> = listOf(
            Parameter("prompt", "string", "test-based prompt for code generation. No code. Just english text.")
        )

        fun build(project: Project, params: Map<String, Any>): CodegenCommand {
            val prompt = params["prompt"] as String
            return CodegenCommand(project, prompt)
        }
    }
}
