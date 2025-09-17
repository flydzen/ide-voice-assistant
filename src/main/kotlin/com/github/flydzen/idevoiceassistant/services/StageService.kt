package com.github.flydzen.idevoiceassistant.services

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.PROJECT)
class StageService {
    private val _stage = MutableStateFlow(Stage.Disabled)
    val stage = _stage.asStateFlow()

    fun setStage(stage: Stage) {
        _stage.value = stage
    }
}


enum class Stage {
    Disabled {
        override fun toString(): String = ""
    },
    Ready {
        override fun toString(): String = "Ready"
    },
    Listening {
        override fun toString(): String = "Listening..."
    },
    Parsing {
        override fun toString(): String = "Parsing..."
    },
    Thinking {
        override fun toString(): String = "Thinking..."
    }
}