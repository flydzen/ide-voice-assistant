package com.github.flydzen.idevoiceassistant.actions

import com.github.flydzen.idevoiceassistant.audio.AudioCaptureTask
import com.github.flydzen.idevoiceassistant.services.RecordAudioService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import org.jetbrains.annotations.NonNls

class RecordAudioAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<RecordAudioService>().start(AudioCaptureTask(4))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    companion object {
        @Suppress("unused")
        @NonNls
        private const val ACTION_ID: String = "RecordAudio"
    }
}