package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.CommandResult
import com.intellij.openapi.project.Project
import kotlin.reflect.full.companionObjectInstance

sealed class Command {
    abstract fun process()
    abstract fun rollback()

    companion object {
        val commands = Command::class.sealedSubclasses.mapNotNull { it.companionObjectInstance as? CommandMeta }

        fun getCommandByName(project: Project, prev: Command?, result: CommandResult): Command {
            val commandName = result.name.lowercase()
            return commands
                .find { it.toolName == commandName }
                ?.build(project, prev, result.params)
                ?: error("Unknown command: $commandName")
        }
    }
}
