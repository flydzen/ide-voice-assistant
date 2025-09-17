package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import java.awt.EventQueue.invokeLater

class IdeActionCommand(private val project: Project, private val actionId: String) : Command() {
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

    companion object : CommandMeta {
        override val toolName: String = "ideAction"
        override val description: String = "" +
                "Run Intellij IDEA action via `ActionManager.getInstance().getAction`. " +
                "Use it only if you know exactly action name."
        override val parameters: List<Parameter> = listOf(
            Parameter("actionId", "string", "actionId of Intellij IDEA action (e.g., ReformatCode, Kotlin.NewFile)")
        )

        override fun build(project: Project, previousCommand: Command?, params: Map<String, Any>): Command = build(project, params)

        fun build(project: Project, params: Map<String, Any>): IdeActionCommand {
            val actionId = params["actionId"] as String
            return IdeActionCommand(project, actionId)
        }
    }
}