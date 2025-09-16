package com.github.flydzen.idevoiceassistant.executor

import com.github.flydzen.idevoiceassistant.commands.Command

class CommandExecutor {
    fun execute(commands: List<Command>) {
        commands.forEach { it.process() }
    }
}
