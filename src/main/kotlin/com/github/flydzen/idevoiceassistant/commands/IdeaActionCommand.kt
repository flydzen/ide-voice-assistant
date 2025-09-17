package com.github.flydzen.idevoiceassistant.commands

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import java.awt.EventQueue.invokeLater

class RunIdeAction(private val actionId: String, private val project: Project) : Command() {
    private var rollbackData: EditorSnapshot? = null

    override fun process() {
        invokeLater {
            val editor = project.service<FileEditorManager>().selectedTextEditor
            editor?.let { rollbackData = EditorSnapshot.create(project, it) }

            val action = ActionManager.getInstance().getAction(actionId) ?: return@invokeLater
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .apply { if (editor != null) add(CommonDataKeys.EDITOR, editor) }
                .build()

            val presentation: Presentation = action.templatePresentation.clone()

            val event = AnActionEvent.createEvent(
                action,
                dataContext,
                presentation,
                ActionPlaces.UNKNOWN,
                ActionUiKind.NONE,
                null
            )
            ActionUtil.performAction(action, event)
        }
    }

    override fun rollback() {
        rollbackData?.rollbackEditor(project)
    }

    override fun toString(): String = "RunIdeAction(actionId=\"$actionId\")"
}