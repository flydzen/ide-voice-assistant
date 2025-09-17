package com.github.flydzen.idevoiceassistant

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType

class Util {
    companion object {
        val LOG: Logger = thisLogger()

        fun <T> timeIt(name: String, block: () -> T): T {
            val start = System.currentTimeMillis()
            val v = block()
            LOG.info("run ${name.padEnd(16)}: ${System.currentTimeMillis() - start} ms")
            return v
        }
    }
}


fun Project.editor(): com.intellij.openapi.editor.Editor? =
    FileEditorManager.getInstance(this).selectedTextEditor ?: run {
        showNotification(this, VoiceAssistantBundle.message("notification.editor.out.of.focus"))
        null
    }

fun showNotification(project: Project, message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Voice Assistant")
        .createNotification(message, MessageType.ERROR)
        .notify(project)
}
