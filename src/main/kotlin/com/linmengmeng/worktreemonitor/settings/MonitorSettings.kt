package com.linmengmeng.worktreemonitor.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service

/**
 * 插件配置(project 级持久化):
 * 1. 每个 worktree 的基准分支配置(未配置时按主 worktree 分支推断);
 * 2. 自动刷新开关与间隔。
 */
@State(name = "AgentWorktreeMonitorSettings", storages = [Storage("agent-worktree-monitor.xml")])
class MonitorSettings : PersistentStateComponent<MonitorSettings.PluginState> {

    data class PluginState(
        var baseBranchByWorktree: MutableMap<String, String> = mutableMapOf(),
        var autoRefreshEnabled: Boolean = true,
        var autoRefreshIntervalSeconds: Int = 10
    )

    private var state = PluginState()

    override fun getState(): PluginState = state

    override fun loadState(loaded: PluginState) {
        state = loaded
    }

    fun baseBranchFor(worktreePath: String): String? = state.baseBranchByWorktree[worktreePath]

    /** branch 传 null 表示清除该 worktree 的显式配置,恢复默认推断 */
    fun setBaseBranch(worktreePath: String, branch: String?) {
        if (branch == null) state.baseBranchByWorktree.remove(worktreePath)
        else state.baseBranchByWorktree[worktreePath] = branch
    }

    var autoRefreshEnabled: Boolean
        get() = state.autoRefreshEnabled
        set(value) { state.autoRefreshEnabled = value }

    var autoRefreshIntervalSeconds: Int
        get() = state.autoRefreshIntervalSeconds
        set(value) { state.autoRefreshIntervalSeconds = value }

    companion object {
        fun getInstance(project: Project): MonitorSettings = project.service<MonitorSettings>()
    }
}
