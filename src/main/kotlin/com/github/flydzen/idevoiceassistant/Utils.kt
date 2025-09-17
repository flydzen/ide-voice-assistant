package com.github.flydzen.idevoiceassistant

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.io.path.createTempFile

object Utils {
    private val LOG: Logger = thisLogger()

    fun <T> timeIt(name: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val v = block()
        LOG.info("run ${name.padEnd(16)}: ${System.currentTimeMillis() - start} ms")
        return v
    }

    fun saveWave(pcmData: ByteArray, prefix: String): File {
        val frameSize = Config.audioFormat.frameSize
        val frameLength = (pcmData.size / frameSize).toLong()
        val stream = ByteArrayInputStream(pcmData)
        val ais = AudioInputStream(stream, Config.audioFormat, frameLength)
        val wavFile = createTempWavFile(prefix)
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile)
        return wavFile
    }

    private fun createTempWavFile(prefix: String): File {
        val file = createTempFile(prefix = prefix, suffix = ".wav").toFile()
        LOG.info("Audio file created: ${file.absolutePath}")
        return file
    }

    fun showNotification(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Voice Assistant")
            .createNotification(message, MessageType.ERROR)
            .notify(project)
    }

    fun Project.editor(): Editor? =
        FileEditorManager.getInstance(this).selectedTextEditor ?: run {
            showNotification(this, VoiceAssistantBundle.message("notification.editor.out.of.focus"))
            null
        }
}
