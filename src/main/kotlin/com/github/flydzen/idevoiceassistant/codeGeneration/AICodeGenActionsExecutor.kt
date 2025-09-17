package com.github.flydzen.idevoiceassistant.codeGeneration

import com.intellij.ide.DataManager
import com.intellij.ml.llm.codeGeneration.AICodeGenerationControllerUtils.getActivePrompt
import com.intellij.ml.llm.codeGeneration.AICodeGenerationUtils
import com.intellij.ml.llm.codeGeneration.actions.AskAiAssistantInEditorAction
import com.intellij.ml.llm.codeGeneration.diff.getAIInEditorDiffViewer
import com.intellij.ml.llm.codeGeneration.inplace.AIDiffBasedCodeGenerationInteraction.Companion.REGENERATE_AS_CODE
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiDocumentManager


object AICodeGenActionsExecutor {

    fun generateCode(promptText: String, editor: Editor) {
        val project = editor.project ?: return
        DumbService.getInstance(project).smartInvokeLater {
            AutoCodeGenerationListener.promptText = promptText

            val psiDoc = PsiDocumentManager.getInstance(project)
            psiDoc.commitDocument(editor.document)
            val psiFile = psiDoc.getPsiFile(editor.document)
            val vFile = psiFile?.virtualFile

            val base = DataManager.getInstance().getDataContext(editor.component)
            val dataContext = SimpleDataContext.builder()
                .setParent(base)
                .add(CommonDataKeys.EDITOR, editor)
                .add(CommonDataKeys.PROJECT, project)
                .apply {
                    if (vFile != null) add(CommonDataKeys.VIRTUAL_FILE, vFile)
                    if (psiFile != null) add(com.intellij.openapi.actionSystem.LangDataKeys.PSI_FILE, psiFile)
                }
                .build()

            val action = AskAiAssistantInEditorAction()
            val event = AnActionEvent.createEvent(
                dataContext,
                action.templatePresentation.clone(),
                "AI_VOICE_ASSISTANT",
                ActionUiKind.NONE,
                null
            )
            action.update(event)
            if (!event.presentation.isEnabledAndVisible) {
                error("Ai Assistant is disabled in current context")
            }
            action.actionPerformed(event)
        }
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