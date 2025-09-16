package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.audio.listeners.AudioListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

@Service(Service.Level.PROJECT)
class RecordAudioService(
    @Suppress("unused") private val project: Project,
    private val scope: CoroutineScope
) {
    private val isActive = AtomicBoolean(false)

    @Volatile
    private var listeners: MutableList<AudioListener> = mutableListOf()

    @get:Synchronized
    private val microphone = createMicrophone()

    private val _inputFlow = MutableStateFlow<Byte?>(null)
    val inputFlow = _inputFlow.asSharedFlow()

    fun start() {
        if (!lock()) {
            LOG.warn("RecordAudioService is already active")
            return
        }

        try {
            triggerOnStart()
            startCapture()
        } catch (e: Exception) {
            triggerOnError(e)
        }
    }

    fun stop() {
        stopCapture()
        triggerOnStop()
        unlock()
    }

    private fun lock(): Boolean {
        return isActive.compareAndSet(false, true)
    }

    private fun unlock() {
        isActive.set(false)
    }

    private fun createMicrophone(): TargetDataLine {
        val info = DataLine.Info(TargetDataLine::class.java, FORMAT)
        val microphone = (AudioSystem.getLine(info) as TargetDataLine)
        microphone.open(FORMAT, BUFFER_SIZE_BYTES)
        return microphone
    }

    private fun startCapture() {
        start()
        scope.launch {
            withContext(Dispatchers.IO) {
                microphone.emitPcmBytes()
            }
        }
    }

    private suspend fun TargetDataLine.emitPcmBytes() {
        val buffer = ByteArray(8192)
        try {
            while (this@RecordAudioService.isActive.get()) {
                val n = read(buffer, 0, buffer.size)
                if (n <= 0) break
                buffer.forEach { byte -> _inputFlow.emit(byte) }
            }
        } catch (t: Throwable) {
            LOG.warn("Capture read interrupted: ${t.message}")
            triggerOnError(t)
        }
    }

    private fun stopCapture() {
        if (microphone.isActive) {
            microphone.stop()
        }
        if (microphone.isOpen) {
            microphone.close()
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
        private val LOG: Logger = thisLogger()

        private const val BUFFER_SIZE_BYTES: Int = 4096

        private val FORMAT: AudioFormat = AudioFormat(
            /* sampleRate = */ 16_000f,
            /* sampleSizeInBits = */ 16,
            /* channels = */ 1,
            /* signed = */ true,
            /* bigEndian = */ false
        )
    }
}