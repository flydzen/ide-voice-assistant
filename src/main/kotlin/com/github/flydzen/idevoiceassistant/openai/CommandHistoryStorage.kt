package com.github.flydzen.idevoiceassistant.openai

import com.github.flydzen.idevoiceassistant.openai.CommandHistoryStorage.CommandHistoryData
import com.intellij.openapi.components.*
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
@State(name = "CommandHistoryStorage", reloadable = true, storages = [Storage("voice_assistant_command_history.xml")])
class CommandHistoryStorage : PersistentStateComponent<CommandHistoryData> {
    private val commands = CopyOnWriteArrayList<String>()

    override fun getState(): CommandHistoryData {
        val state = CommandHistoryData()
        state.commands = commands
        return state
    }

    override fun loadState(state: CommandHistoryData) {
        commands.clear()
        commands.addAll(state.commands)
    }

    fun getLastNCommands(n: Int) = commands.takeLast(n)

    fun addCommand(command: String) = commands.add(command)

    class CommandHistoryData : BaseState() {
        var commands by list<String>()
    }
}