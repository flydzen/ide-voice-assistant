package com.github.flydzen.idevoiceassistant.audio

import com.github.flydzen.idevoiceassistant.Config
import com.github.flydzen.idevoiceassistant.audio.listeners.AudioListener
import com.github.flydzen.idevoiceassistant.audio.listeners.LogListener
import com.github.flydzen.idevoiceassistant.openai.OpenAIClient
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*
import kotlin.io.path.createTempFile

fun saveWave(pcmData: ByteArray, wavFile: File) {
    val frameSize = Config.audioFormat.frameSize
    val frameLength = (pcmData.size / frameSize).toLong()
    val stream = ByteArrayInputStream(pcmData)
    val ais = AudioInputStream(stream, Config.audioFormat, frameLength)
    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile)
}

class AudioCaptureTask(seconds: Int) {
    private val duration = TimeUnit.SECONDS.toMillis(seconds.toLong())

    @Volatile
    private var listeners: MutableList<AudioListener> = mutableListOf()

    init {
        require(seconds > 0) { "Seconds must be positive" }
        require(seconds <= MAX_TIME_SECONDS) { "Seconds must be less than $MAX_TIME_SECONDS" }

        listeners.add(LogListener)
    }

    suspend fun run() = coroutineScope {
        val microphone = createMicrophone()
        val tempFile = createTempFile()

        val stopper = launch {
            delay(duration)
            microphone.stopCapture()
        }

        try {
            triggerOnStart()
            microphone.startCapture(tempFile)
        } catch (e: Exception) {
            triggerOnError(e)
        } finally {
            stopper.cancel()
            microphone.stopCapture()
            triggerOnStop()
        }

        val text = OpenAIClient.speech2Text(tempFile)
        println("text: $text")
        val command = OpenAIClient.textToCommand(text)
        println("command: $command")
    }

    private fun createMicrophone(): TargetDataLine {
        val info = DataLine.Info(TargetDataLine::class.java, Config.audioFormat)
        val microphone = (AudioSystem.getLine(info) as TargetDataLine)
        microphone.open(Config.audioFormat, BUFFER_SIZE_BYTES)
        return microphone
    }

    private fun createTempFile(): File {
        val file = createTempFile(prefix = "recording-", suffix = ".wav").toFile()
        LOG.info("Audio file created: ${file.absolutePath}")
        return file
    }

    private suspend fun TargetDataLine.startCapture(file: File) {
        start()
        withContext(Dispatchers.IO) {
            val pcmData = this@startCapture.retrievePcmArray()
            saveWave(pcmData, file)
        }
    }

    private fun TargetDataLine.retrievePcmArray(): ByteArray {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        try {
            while (true) {
                val n = read(buffer, 0, buffer.size)
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
        } catch (t: Throwable) {
            LOG.warn("Capture read interrupted: ${t.message}")
            triggerOnError(t)
        }
        return out.toByteArray()
    }

    private fun TargetDataLine.stopCapture() {
        if (isActive) {
            stop()
        }
        if (isOpen) {
            close()
        }
    }

    private fun triggerOnStart() {
        listeners.forEach { it.onStart() }
    }

    private fun triggerOnStop() {
        listeners.forEach { it.onStop() }
    }

    private fun triggerOnError(t: Throwable) {
        listeners.forEach { it.onError(t) }
    }

    companion object {
        private const val BUFFER_SIZE_BYTES: Int = 4096
        private const val MAX_TIME_SECONDS: Int = 60

        private val LOG: Logger = thisLogger()
    }
}