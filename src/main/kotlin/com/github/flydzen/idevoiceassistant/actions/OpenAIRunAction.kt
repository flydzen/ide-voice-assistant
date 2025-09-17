package com.github.flydzen.idevoiceassistant.actions

import com.github.flydzen.idevoiceassistant.executor.CommandExecutor
import com.github.flydzen.idevoiceassistant.openai.OpenAIClient
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.NonNls

class OpenAIRunAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project

        if (project == null) {
            Messages.showErrorDialog("No project is open", "Error")
            return
        }

        val textToProcess = getTextToProcess(editor)

        if (textToProcess == null || textToProcess.isEmpty()) {
            Messages.showInfoMessage("No text selected or cursor is empty", "Info")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val commands = OpenAIClient.textToCommand(project, textToProcess)
                println(commands)
                project.service<CommandExecutor>().execute(project, commands)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        "Error running OpenAI request: ${e.message}",
                        "OpenAI Error"
                    )
                }
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getTextToProcess(editor: Editor?): String? {
        if (editor == null) return ""

        val selectionModel = editor.selectionModel

        if (selectionModel.hasSelection()) {
            return selectionModel.selectedText ?: ""
        }
        return null
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    companion object {
        @Suppress("unused")
        @NonNls
        private const val ACTION_ID: String = "OpenAIRunAction"
    }
}