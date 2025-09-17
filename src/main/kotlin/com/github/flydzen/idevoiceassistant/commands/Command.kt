package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.VoiceAssistantBundle
import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.github.flydzen.idevoiceassistant.editor
import com.github.flydzen.idevoiceassistant.showNotification
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.vfs.VirtualFile

sealed class Command {
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

    class DirNavigate(val dirName: String) : Command() {
        override fun process() {
            TODO("Not yet implemented")
        }
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
            showNotification(project, "Not recognized: $text")
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
}
