package com.github.flydzen.idevoiceassistant.services

import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.vim.api.models.Mode
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.thinapi.changeMode

@Service(Service.Level.PROJECT)
class VimScriptExecutionService(private val project: Project) {
    /**
     * Executes the specified Vim script and returns the result of the execution.
     *
     * @param vimScript the Vim script to be executed
     * @return true if the execution was successful, false otherwise
     */
    @Suppress("UnstableApiUsage")
    suspend fun execute(vimScript: String): Boolean {
        return writeCommandAction(project, "Vim Script Execution") {
            val vimEditor = injector.editorGroup.getFocusedEditor()
            if (vimEditor == null) {
                LOG.error("No focused editor")
                return@writeCommandAction false
            }
            changeMode(Mode.NORMAL, vimEditor)
            val vimContext = injector.executionContextManager.getEditorExecutionContext(vimEditor)
            val result = injector.vimscriptExecutor.execute(
                vimScript, vimEditor, vimContext,
                skipHistory = true,
                indicateErrors = true
            )
            changeMode(Mode.INSERT, vimEditor)
            result == com.maddyhome.idea.vim.vimscript.model.ExecutionResult.Success
        }
    }

    companion object {
        private val LOG = logger<VimScriptExecutionService>()
    }
}