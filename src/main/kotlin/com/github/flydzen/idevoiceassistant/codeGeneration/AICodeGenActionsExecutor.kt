package com.github.flydzen.idevoiceassistant.codeGeneration

import com.intellij.ml.llm.codeGeneration.AICodeGenerationControllerUtils.getActivePrompt
import com.intellij.ml.llm.codeGeneration.AICodeGenerationUtils
import com.intellij.ml.llm.codeGeneration.actions.AskAiAssistantInEditorAction
import com.intellij.ml.llm.codeGeneration.diff.getAIInEditorDiffViewer
import com.intellij.ml.llm.codeGeneration.inplace.AIDiffBasedCodeGenerationInteraction.Companion.REGENERATE_AS_CODE
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer


object AICodeGenActionsExecutor {

    fun generateCode(e: AnActionEvent) {
        val action = AskAiAssistantInEditorAction()
        action.actionPerformed(e)
    }

    fun acceptAllChanges(editor: Editor) {
        val diffViewer = editor.getAIInEditorDiffViewer()
        diffViewer?.acceptAllChanges()
        editor.getActivePrompt()?.let { Disposer.dispose(it, false) }
    }

    fun regenerate(editor: Editor) {
        val dataContext = SimpleDataContext.builder().add(REGENERATE_AS_CODE, true).build()
        editor.getActivePrompt()?.onRegenerate?.invoke(dataContext)
    }

    fun stop(editor: Editor) {
        val dataContext = SimpleDataContext.builder().build()
        AICodeGenerationUtils.stopCodeGeneration(editor, editor.project, dataContext)
    }

    fun discard(editor: Editor) {
        val diffViewer = editor.getAIInEditorDiffViewer()
        diffViewer?.discardAllChanges()
        editor.getActivePrompt()?.let { Disposer.dispose(it, false) }
    }

}