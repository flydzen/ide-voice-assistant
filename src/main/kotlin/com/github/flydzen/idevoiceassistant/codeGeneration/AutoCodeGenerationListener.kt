package com.github.flydzen.idevoiceassistant.codeGeneration

import com.intellij.ml.llm.codeGeneration.AICodeGenerationListener
import com.intellij.ml.llm.codeGeneration.AICodeGenerationProgressListener
import com.intellij.ml.llm.codeGeneration.popup.AICodeGenerationPopupUtils.getActivePromptInCode
import com.intellij.ml.llm.core.chat.session.ChatSessionStorage.SourceAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil


class AutoCodeGenerationListener(private val project: Project) : AICodeGenerationListener {
    override fun onCodeGenerationPromptShown() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        javax.swing.SwingUtilities.invokeLater {
            val controller = editor.getActivePromptInCode() ?: return@invokeLater
            val popupComponent = controller.component
            val editorTextField = UIUtil.findComponentOfType(popupComponent, EditorTextField::class.java) ?: return@invokeLater
            editorTextField.text = promptText
            AICodeGenerationProgressListener.submit(editor, SourceAction.GENERATE_CODE_INPLACE)
        }
    }

    companion object {
        var promptText: String = ""
    }
}