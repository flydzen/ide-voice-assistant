package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.project.Project

sealed class Command {
    abstract val toolName: String
    abstract val description: String
    abstract val parameters: List<Parameter>
    abstract val build: (project: Project, previousCommand: Command?, params: Map<String, Any>) -> Command?

    abstract fun process()
    abstract fun rollback()
}
