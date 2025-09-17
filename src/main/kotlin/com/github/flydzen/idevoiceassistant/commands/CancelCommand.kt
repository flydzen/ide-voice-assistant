package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.project.Project

class CancelCommand(val project: Project, val previousCommand: Command?) : Command() {
    override val toolName = "cancel"
    override val description: String = "Cancel previous command"
    override val parameters: List<Parameter> = emptyList()
    override val build: (project: Project, previousCommand: Command?, params: Map<String, Any>) -> Command? = { project, prev, _ ->
        CancelCommand(project, prev)
    }

    override fun process() {
        previousCommand?.rollback()
    }

    override fun rollback() {}
}