package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.project.Project

class CancelCommand(private val previousCommand: Command?) : Command() {

    override fun process() {
        previousCommand?.rollback()
    }

    override fun rollback() {}

    companion object : CommandMeta {
        override val toolName = "cancel"
        override val description: String = "Cancel previous command"
        override val parameters: List<Parameter> = emptyList()

        override fun build(project: Project, previousCommand: Command?, params: Map<String, Any>): Command = CancelCommand(previousCommand)

        fun build(previousCommand: Command?): CancelCommand = CancelCommand(previousCommand)
    }
}