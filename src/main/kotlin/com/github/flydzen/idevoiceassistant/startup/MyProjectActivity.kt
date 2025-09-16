package com.github.flydzen.idevoiceassistant.startup

import com.github.flydzen.idevoiceassistant.codeGeneration.AutoCodeGenerationListener
import com.intellij.ml.llm.codeGeneration.AICodeGenerationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.vim.api.models.Mode
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.Options
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.group.IjOptionConstants
import com.maddyhome.idea.vim.newapi.globalIjOptions
import com.maddyhome.idea.vim.options.OptionAccessScope
import com.maddyhome.idea.vim.thinapi.changeMode
import com.maddyhome.idea.vim.ui.widgets.macro.macroWidgetOptionListener
import com.maddyhome.idea.vim.ui.widgets.mode.modeWidgetOptionListener
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimInt

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        disableVimOptions()
        injector.globalIjOptions().ideastatusicon = IjOptionConstants.ideastatusicon_gray
        changeMode(Mode.INSERT, null)

        project.messageBus.connect()
            .subscribe(AICodeGenerationListener.TOPIC, AutoCodeGenerationListener(project))
    }

    private fun disableVimOptions() {
        val optionGroup = VimPlugin.getOptionGroup()
        //optionGroup.removeEffectiveOptionValueChangeListener(Options.scrolloff, ScrollGroup.ScrollOptionsChangeListener)
        //optionGroup.removeGlobalOptionChangeListener(Options.showcmd, ShowCmdOptionChangeListener)
        optionGroup.removeGlobalOptionChangeListener(Options.showmode, modeWidgetOptionListener)
        optionGroup.removeGlobalOptionChangeListener(Options.showmode, macroWidgetOptionListener)
        injector.optionGroup.setOptionValue(Options.showmode, OptionAccessScope.GLOBAL(null), VimInt(0))
        //optionGroup.removeEffectiveOptionValueChangeListener(Options.guicursor, GuicursorChangeListener)
    }
}