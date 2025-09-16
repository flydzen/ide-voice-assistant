package com.github.flydzen.idevoiceassistant.audio.listeners

import com.intellij.openapi.diagnostic.thisLogger

object LogListener : AudioListener {
    private val LOG = thisLogger()

    override fun onStart() {
        LOG.info("Audio capture started")
    }

    override fun onStop() {
        LOG.info("Audio capture stopped")
    }

    override fun onError(t: Throwable) {
        LOG.error("Audio capture error", t)
    }
}