package com.github.flydzen.idevoiceassistant.actions

import com.github.flydzen.idevoiceassistant.services.VimScriptExecutionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JComponent

class ExecuteVimScriptAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        currentThreadCoroutineScope().launch {
            val selectedEditor = e.getData(CommonDataKeys.EDITOR)
                ?: FileEditorManager.getInstance(project).selectedTextEditor
                ?: return@launch

            val (inputCode, script) = showVimScriptDialog(project)
            if (!inputCode || script == null) return@launch

            IdeFocusManager.getInstance(project)
                .requestFocus(selectedEditor.contentComponent, true)

            project.service<VimScriptExecutionService>().execute(script)
        }
    }


    private suspend fun showVimScriptDialog(project: Project): Pair<Boolean, String?> = withContext(Dispatchers.EDT) {
        val dialog = InputVimScriptDialog(project)
        dialog.show()
        dialog.isOK to dialog.vimScript
    }
}

private class InputVimScriptDialog(project: Project) : DialogWrapper(project) {
    private var text: JBTextField? = null

    init {
        title = "Enter Vim Script"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                text = textField()
                    .align(Align.FILL)
                    .applyToComponent {
                        emptyText.text = "Enter Vim Script"
                    }
                    .component
            }.resizableRow()
        }
    }

    val vimScript: String?
        get() = text?.text
}