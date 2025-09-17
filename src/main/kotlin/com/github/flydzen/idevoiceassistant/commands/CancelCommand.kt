package com.github.flydzen.idevoiceassistant.commands

import com.intellij.openapi.project.Project

class CancelCommand(val project: Project, val previousCommand: Command?) : Command() {
    override fun process() {
        previousCommand?.rollback()
    }

    override fun rollback() {}
}