package com.github.flydzen.idevoiceassistant.audio.listeners

interface AudioListener {
    fun onStart()
    fun onStop()
    fun onError(t: Throwable)
}