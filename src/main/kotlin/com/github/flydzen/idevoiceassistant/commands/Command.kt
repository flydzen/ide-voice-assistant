package com.github.flydzen.idevoiceassistant.commands

sealed class Command {
    abstract fun process()
    abstract fun rollback()
}
