package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils
import com.github.flydzen.idevoiceassistant.Utils.editor
import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.github.flydzen.idevoiceassistant.executor.CommandExecutor
import com.github.flydzen.idevoiceassistant.openai.OpenAIClient
import com.github.flydzen.idevoiceassistant.services.VimScriptExecutionService
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

sealed class Command {
    protected val LOG = thisLogger()

    abstract fun process()

    class EnterText(val text: String, val project: Project) : Command() {
        override fun process() {
            invokeLater {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor ?: return@invokeLater

                WriteCommandAction.runWriteCommandAction(project) {
                    val document = editor.document
                    val caretModel = editor.caretModel
                    val offset = caretModel.offset

                    document.insertString(offset, text)
                    caretModel.moveToOffset(offset + text.length)
                }

            }
        }

        override fun toString(): String = "EnterText(text='$text')"
    }

    class FileNavigate(
        val fileName: String,
        val project: Project,
        val packagePrefix: String? = null,
    ) : Command() {
        override fun process() {
            invokeLater {
                // Find files by name
                val files = FilenameIndex.getFilesByName(
                    project,
                    fileName,
                    GlobalSearchScope.projectScope(project)
                )

                val targetFile = if (packagePrefix != null) {
                    // Filter by package prefix if provided
                    files.find { file ->
                        val filePath = file.virtualFile.path
                        val packagePath = packagePrefix.replace('.', '/')
                        filePath.contains(packagePath)
                    }
                } else {
                    // Take first match if no package prefix
                    files.firstOrNull()
                }

                targetFile?.let { file ->
                    openFileInEditor(file.virtualFile, project)
                }
            }
        }

        private fun openFileInEditor(file: VirtualFile, project: Project, line: Int? = null) {
            val fileEditorManager = FileEditorManager.getInstance(project)

            val editor = fileEditorManager.openTextEditor(
                com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file),
                true
            )

            if (line != null && editor != null) {
                val document = editor.document
                val lineCount = document.lineCount

                val targetLine = (line - 1).coerceIn(0, lineCount - 1)
                val offset = document.getLineStartOffset(targetLine)

                editor.caretModel.moveToOffset(offset)
                editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
            }
        }
    }

    class RunIdeAction(private val actionId: String, private val project: Project) : Command() {
        override fun process() {
            invokeLater {
                val action = ActionManager.getInstance().getAction(actionId) ?: return@invokeLater

                val editor = FileEditorManager.getInstance(project).selectedTextEditor
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

        override fun toString(): String = "RunIdeAction(actionId=\"$actionId\")"
    }


    class Codegen(private val prompt: String, private val project: Project) : Command() {
        override fun process() {
            invokeLater {
                val editor = project.editor() ?: return@invokeLater
                AICodeGenActionsExecutor.generateCode(prompt, editor)
            }
        }
    }

    class NotificationCommand(private val text: String, val project: Project) : Command() {
        override fun process() {
            Utils.showNotification(project, "Not recognized: $text")
        }
    }

    class Cancel(val project: Project) : Command() {
        override fun process() {
            invokeLater {
                val editor = project.editor() ?: return@invokeLater
                AICodeGenActionsExecutor.discard(editor)
            }
            // TODO: cancel other commands
        }
    }

    class Approve(val project: Project) : Command() {
        override fun process() {
            invokeLater {
                val editor = project.editor() ?: return@invokeLater
                AICodeGenActionsExecutor.acceptAllChanges(editor)
            }
            // TODO: approve other commands
        }
    }

    class Stop(val project: Project) : Command() {
        override fun process() {
            invokeLater {
                val editor = project.editor() ?: return@invokeLater
                AICodeGenActionsExecutor.stop(editor)
            }
            // TODO: stop other commands
        }
    }

    class VimCommand(val project: Project, val command: String) : Command() {
        override fun process() {
            invokeLater {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editorComponent = fileEditorManager.selectedTextEditor?.contentComponent
                if (editorComponent != null) {
                    IdeFocusManager.getInstance(project).requestFocus(editorComponent, true)
                }
                val modifiedScript = modifyVimCommandToVimScript(command)
                VimScriptExecutionService.getInstance(project).execute(modifiedScript)
            }
        }

        private fun modifyVimCommandToVimScript(command: String): String {
            return if (!command.startsWith(":")) {
                ":normal $command<cr>"
            }
            else command
        }
    }
}
