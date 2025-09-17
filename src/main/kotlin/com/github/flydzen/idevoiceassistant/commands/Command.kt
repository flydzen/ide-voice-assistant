package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils
import com.github.flydzen.idevoiceassistant.Utils.editor
import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.github.flydzen.idevoiceassistant.services.VimScriptExecutionService
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

sealed class Command {
    abstract fun process()
    abstract fun rollback()
    
    class EnterText(val text: String, val project: Project) : Command() {
        private var rollbackData: RollbackData? = null

        data class RollbackData(
            val insertOffset: Int,
            val insertLength: Int,
            val virtualFile: VirtualFile?
        )

        override fun process() {
            invokeLater {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor ?: return@invokeLater
                val offset = editor.caretModel.offset

                rollbackData = RollbackData(
                    insertOffset = offset,
                    insertLength = text.length,
                    virtualFile = fileEditorManager.selectedFiles.firstOrNull()
                )

                WriteCommandAction.runWriteCommandAction(project) {
                    val document = editor.document
                    val caretModel = editor.caretModel
                    val offset = caretModel.offset

                    document.insertString(offset, text)
                    caretModel.moveToOffset(offset + text.length)
                }

            }
        }

        override fun rollback() {
            invokeLater {
                val data = rollbackData ?: return@invokeLater
                val fileEditorManager = FileEditorManager.getInstance(project)

                val currentFile = fileEditorManager.selectedFiles.firstOrNull()
                if (currentFile != data.virtualFile) {
                    data.virtualFile?.let { file ->
                        fileEditorManager.openFile(file, true)
                    }
                }

                val editor = fileEditorManager.selectedTextEditor ?: return@invokeLater

                WriteCommandAction.runWriteCommandAction(project) {
                    val document = editor.document
                    val endOffset = data.insertOffset + data.insertLength

                    if (endOffset <= document.textLength) {
                        val actualText =
                            document.getText(com.intellij.openapi.util.TextRange(data.insertOffset, endOffset))
                        if (actualText == text) {
                            document.deleteString(data.insertOffset, endOffset)
                            editor.caretModel.moveToOffset(data.insertOffset)
                        }
                    }
                }
            }
        }

        override fun toString(): String = "EnterText(text='$text')"
    }

    class CreateFile(
        private val path: String,
        private val project: Project
    ) : Command() {
        private val LOG = thisLogger()

        override fun process() {
            val baseDir = resolveBaseDirectory() ?: run {
                LOG.warn("CreateFile: base directory not resolved")
                return
            }

            val parts = path.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
            if (parts.isEmpty()) {
                LOG.warn("CreateFile: empty path")
                return
            }

            invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        val fileName = parts.last()
                        val parent = ensureSubdirs(baseDir, parts.dropLast(1))

                        val file = parent.createChildData(this, fileName)
                        FileEditorManager.getInstance(project).openFile(file, true)
                        LOG.info("CreateFile: created ${file.path}")
                    } catch (t: Throwable) {
                        LOG.warn("CreateFile failed for '$path': ${t.message}", t)
                    }
                }
            }
        }

        private fun resolveBaseDirectory(): com.intellij.openapi.vfs.VirtualFile? {
            // 1) Папка текущего открытого файла (если есть)
            project.editor()?.document?.let { doc ->
                val file = FileDocumentManager.getInstance().getFile(doc)
                file?.parent?.let { return it }
            }
            // 2) Иначе — корень проекта
            return project.basePath?.let { VfsUtil.createDirectories(it) }
        }

        private fun ensureSubdirs(root: com.intellij.openapi.vfs.VirtualFile, subdirs: List<String>): com.intellij.openapi.vfs.VirtualFile {
            var current = root
            for (seg in subdirs) {
                current = current.findChild(seg) ?: current.createChildDirectory(this, seg)
            }
            return current
        }

        override fun rollback() {
            TODO("Not yet implemented")
        }

        override fun toString(): String = "CreateFile(path=\"$path\")"
    }

    class FileNavigate(
        val fileName: String,
        val project: Project,
        val packagePrefix: String? = null,
    ) : Command() {
        private var rollbackData: RollbackData? = null

        data class RollbackData(
            val previousFile: VirtualFile?,
            val previousCaretOffset: Int
        )

        override fun process() {
            invokeLater {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val currentEditor = fileEditorManager.selectedTextEditor

                rollbackData = RollbackData(
                    previousFile = fileEditorManager.selectedFiles.firstOrNull(),
                    previousCaretOffset = currentEditor?.caretModel?.offset ?: 0
                )

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

        override fun rollback() {
            val data = rollbackData ?: return

            invokeLater {
                val fileEditorManager = FileEditorManager.getInstance(project)

                data.previousFile?.let { file ->
                    val editor = fileEditorManager.openTextEditor(
                        OpenFileDescriptor(project, file),
                        true
                    )

                    editor?.caretModel?.moveToOffset(data.previousCaretOffset)
                }
            }
        }


        private fun openFileInEditor(file: VirtualFile, project: Project, line: Int? = null) {
            val fileEditorManager = FileEditorManager.getInstance(project)

            val editor = fileEditorManager.openTextEditor(
                OpenFileDescriptor(project, file),
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
        override fun toString(): String = "FileNavigate(fileName='$fileName')"
    }

    class Codegen(private val prompt: String, private val project: Project) : Command() {
        override fun process() {
            invokeLater {
                val editor = project.editor() ?: return@invokeLater
                AICodeGenActionsExecutor.generateCode(prompt, editor)
            }
        }

        override fun rollback() {
            invokeLater {
                val editor = project.editor() ?: return@invokeLater
                AICodeGenActionsExecutor.discard(editor)
            }
        }
        override fun toString(): String = "Codegen(prompt='$prompt')"
    }

    class NotificationCommand(private val text: String, val project: Project) : Command() {
        override fun process() {
            Utils.showNotification(project, "Not recognized: $text")
        }

        override fun rollback() {}
    }

    class Cancel(val project: Project, val previousCommand: Command?) : Command() {
        override fun process() {
            previousCommand?.rollback()
        }

        override fun rollback() {}
    }

    class Approve(val project: Project) : Command() {
        private var rollbackData: RollbackEditorData? = null

        override fun process() {
            invokeLater {
                val editor = project.service<FileEditorManager>().selectedTextEditor ?: return@invokeLater
                rollbackData = collectEditorRollbackData(project, editor)
                AICodeGenActionsExecutor.acceptAllChanges(editor)
            }
            // TODO: approve other commands
        }

        override fun rollback() {
            rollbackData?.rollbackEditor(project)
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

        override fun rollback() {}
    }

    class VimCommand(val command: String, val project: Project) : Command() {
        private var rollbackData: RollbackEditorData? = null

        override fun process() {
            invokeLater {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editorComponent = fileEditorManager.selectedTextEditor?.contentComponent
                if (editorComponent != null) {
                    IdeFocusManager.getInstance(project).requestFocus(editorComponent, true)
                }
                val editor = fileEditorManager.selectedTextEditor ?: return@invokeLater
                rollbackData = collectEditorRollbackData(project, editor)
                val modifiedScript = modifyVimCommandToVimScript(command)
                println("Vim original command: $command")
                println("Vim modified command: $modifiedScript")
                VimScriptExecutionService.getInstance(project).execute(modifiedScript)
            }
        }

        private fun modifyVimCommandToVimScript(command: String): String {
            return if (!command.startsWith(":")) {
                ":normal $command<cr>"
            } else command
        }

        override fun rollback() {
            rollbackData?.rollbackEditor(project)
        }

        override fun toString(): String = "VimCommand(command='$command')"
    }

    class RollbackEditorData(
        val editorState: EditorState?
    )

    data class EditorState(
        val virtualFile: VirtualFile,
        val documentText: String,
        val caretOffset: Int,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    companion object {
        fun RollbackEditorData.rollbackEditor(project: Project) {
            val data = editorState ?: return

            invokeLater {
                val editor = project.service<FileEditorManager>().openTextEditor(
                    OpenFileDescriptor(project, data.virtualFile),
                    true
                ) ?: return@invokeLater

                WriteCommandAction.runWriteCommandAction(project) {
                    if (editor.document.text != data.documentText) {
                        editor.document.setText(data.documentText)
                    }

                    editor.caretModel.moveToOffset(data.caretOffset)
                    if (data.selectionStart != data.selectionEnd) {
                        editor.selectionModel.setSelection(data.selectionStart, data.selectionEnd)
                    }
                }
            }
        }

        fun collectEditorRollbackData(project: Project, editor: Editor): RollbackEditorData {
            return RollbackEditorData(
                editorState = project.service<FileEditorManager>().selectedFiles.firstOrNull()?.let { file ->
                    EditorState(
                        virtualFile = file,
                        documentText = editor.document.text,
                        caretOffset = editor.caretModel.offset,
                        selectionStart = editor.selectionModel.selectionStart,
                        selectionEnd = editor.selectionModel.selectionEnd
                    )
                }
            )
        }
    }
}
