package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils
import com.intellij.openapi.project.Project

class NotificationCommand(private val text: String, val project: Project) : Command() {
    override fun process() {
        Utils.showNotification(project, "Not recognized: $text")
    }

    override fun rollback() {}
}