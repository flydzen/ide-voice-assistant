package com.github.flydzen.idevoiceassistant

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger

class TimeIt {
    companion object {
        private val LOG: Logger = thisLogger()

        fun <T> timeIt(name: String, block: () -> T): T {
            val start = System.currentTimeMillis()
            val v =block()
            LOG.info("run ${name.padEnd(16)}: ${System.currentTimeMillis() - start} ms")
            return v
        }
    }

}
