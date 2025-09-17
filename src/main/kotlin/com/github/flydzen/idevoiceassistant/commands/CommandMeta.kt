package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.project.Project

interface CommandMeta {
    val toolName: String
    val description: String
    val parameters: List<Parameter>

    fun build(project: Project, previousCommand: Command?, params: Map<String, Any>): Command
}
