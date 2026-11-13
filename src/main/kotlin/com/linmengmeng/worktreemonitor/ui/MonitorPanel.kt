package com.linmengmeng.worktreemonitor.ui

import com.linmengmeng.worktreemonitor.domain.FileChange
import com.linmengmeng.worktreemonitor.domain.FileStatus
import com.linmengmeng.worktreemonitor.domain.MergedFileChange
import com.linmengmeng.worktreemonitor.domain.WorktreeChange
import com.linmengmeng.worktreemonitor.domain.WorktreeInfo
import com.linmengmeng.worktreemonitor.domain.mergedFiles
import com.linmengmeng.worktreemonitor.domain.statusText
import com.linmengmeng.worktreemonitor.git.OverlapDetector
import com.linmengmeng.worktreemonitor.git.OverlapItem
import com.linmengmeng.worktreemonitor.git.WorktreeScanner
import com.linmengmeng.worktreemonitor.service.DiffOpener
import com.linmengmeng.worktreemonitor.settings.MonitorSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTabbedPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.table.AbstractTableModel
import kotlin.io.path.readText

/**
 * Agent Worktree Monitor 主面板:
 * 上:刷新工具栏(含自动刷新开关与间隔);中:Tab1 worktree 总览/文件明细、Tab2 跨 worktree 重叠;下:状态栏。
 * 所有 git 命令在后台线程执行,结果回 EDT 更新;自动刷新采用轻量签名比对,状态未变化时跳过详细扫描。
 */
class MonitorPanel(private val project: Project) : JBPanel<JBPanel<*>>(BorderLayout()), Disposable {

    private data class WorktreeRow(
        val info: WorktreeInfo,
        val change: WorktreeChange?,
        val error: String?
    )

    private data class ScanResult(
        val rows: List<WorktreeRow>,
        val overlaps: List<OverlapItem>,
        val signature: String
    )

    /** 重叠表行:文件 × 涉及方(每方一行,双击直接打开该方 Diff) */
    private data class OverlapRow(
        val filePath: String,
        val info: WorktreeInfo,
        val change: WorktreeChange?,
        val totalOwners: Int
    )

    private val scanner = WorktreeScanner()
    private val executor = com.linmengmeng.worktreemonitor.git.GitCommandExecutor()
    private val diffOpener = DiffOpener(project)
    private val settings = MonitorSettings.getInstance(project)

    private val worktreeListModel = DefaultListModel<WorktreeRow>()
    private val worktreeList = JBList(worktreeListModel)
    private val fileTableModel = FileTableModel()
    private val fileTable = JBTable(fileTableModel)
    private val overlapTableModel = OverlapTableModel()
    private val overlapTable = JBTable(overlapTableModel)
    private val refreshButton = JButton("刷新")
    private val autoRefreshCheck = JBCheckBox("自动刷新")
    private val intervalCombo = JComboBox(arrayOf(5, 10, 30, 60))
    private val statusLabel = JBLabel("未刷新")

    private var refreshing = false
    private var lastWorktrees: List<WorktreeInfo> = emptyList()
    private var lastSignature: String? = null
    private var autoTimer: Timer? = null

    init {
        worktreeList.cellRenderer = WorktreeCellRenderer()
        worktreeList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        worktreeList.addListSelectionListener { updateFileTable() }
        worktreeList.componentPopupMenu = createBaseBranchMenu()

        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        fileTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openSelectedFileDiff()
            }
        })

        overlapTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        overlapTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openSelectedOverlapDiff()
            }
        })

        refreshButton.addActionListener { scheduleScan(light = false) }
        autoRefreshCheck.isSelected = settings.autoRefreshEnabled
        autoRefreshCheck.addActionListener {
            settings.autoRefreshEnabled = autoRefreshCheck.isSelected
            restartAutoRefresh()
        }
        intervalCombo.selectedItem = settings.autoRefreshIntervalSeconds
        intervalCombo.addActionListener {
            val selected = intervalCombo.selectedItem as? Int ?: return@addActionListener
            settings.autoRefreshIntervalSeconds = selected
            restartAutoRefresh()
        }

        val toolbar = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(refreshButton)
            add(autoRefreshCheck)
            add(JBLabel("间隔(秒):"))
            add(intervalCombo)
        }

        val listScroll: JScrollPane = JBScrollPane(worktreeList)
        val tableScroll: JScrollPane = JBScrollPane(fileTable)
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, tableScroll)
        splitPane.resizeWeight = 0.45

        val tabs = JTabbedPane()
        tabs.addTab("Worktrees", splitPane)
        tabs.addTab("重叠", JBScrollPane(overlapTable))

        add(toolbar, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            ApplicationActivationListener.TOPIC,
            object : ApplicationActivationListener {
                override fun applicationActivated(ideFrame: IdeFrame) {
                    scheduleScan(light = true)
                }
            }
        )
        restartAutoRefresh()
    }

    /** 手动刷新入口 */
    fun refresh() = scheduleScan(light = false)

    // ---------- 扫描 ----------

    private fun scheduleScan(light: Boolean) {
        val projectDir = project.basePath?.let { Path.of(it) }
        if (projectDir == null) {
            statusLabel.text = "当前项目无本地目录,无法扫描"
            return
        }
        if (refreshing) return
        refreshing = true
        refreshButton.isEnabled = false
        if (!light) statusLabel.text = "扫描中…"

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (light) {
                    val signature = computeSignature(projectDir)
                    if (signature != null && signature == lastSignature) {
                        return@executeOnPooledThread
                    }
                }
                val result = fullScan(projectDir)
                SwingUtilities.invokeLater { updateUi(result, projectDir, light) }
            } catch (e: Exception) {
                thisLogger().warn("AgentWorktreeMonitor 扫描失败: ${e.message}")
                SwingUtilities.invokeLater { statusLabel.text = "扫描失败:${e.message}" }
            } finally {
                refreshing = false
                SwingUtilities.invokeLater { refreshButton.isEnabled = true }
            }
        }
    }

    private fun fullScan(projectDir: Path): ScanResult {
        val worktrees = scanner.scanWorktrees(projectDir)
        lastWorktrees = worktrees

        val defaultBase = worktrees.firstOrNull()
            ?.takeIf { !it.isDetached }?.branchDisplayName
        val rows = worktrees.map { wt ->
            try {
                val explicitBase = settings.baseBranchFor(wt.path)
                val base = explicitBase ?: if (wt.isMainWorktree) null else defaultBase
                WorktreeRow(wt, scanner.scanChanges(wt, base), null)
            } catch (e: Exception) {
                WorktreeRow(wt, null, e.message)
            }
        }
        val overlaps = OverlapDetector.detect(rows.mapNotNull { it.change })
        return ScanResult(rows, overlaps, computeSignature(projectDir) ?: "")
    }

    /**
     * 轻量签名:worktree 列表 + 各 worktree status 输出的 CRC。
     * 签名不变则跳过详细扫描(numstat / 分支 diff / log 都不执行)。
     */
    private fun computeSignature(projectDir: Path): String? {
        return try {
            val sb = StringBuilder()
            val wtResult = executor.execute(projectDir, "worktree", "list", "--porcelain", "-z")
            sb.append(crc(wtResult.stdoutBytes)).append('|')
            for (wt in lastWorktrees) {
                val st = executor.execute(Path.of(wt.path), "status", "--porcelain=v1", "-z", "--untracked-files=all")
                sb.append(crc(st.stdoutBytes)).append('|')
            }
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun crc(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    // ---------- UI 更新 ----------

    private fun updateUi(result: ScanResult, projectDir: Path, light: Boolean) {
        lastSignature = result.signature.ifEmpty { null }
        worktreeListModel.removeAllElements()
        result.rows.forEach { worktreeListModel.addElement(it) }
        if (result.rows.isNotEmpty() && worktreeList.selectedIndex < 0) {
            worktreeList.selectedIndex = 0
        }
        overlapTableModel.setRows(flattenOverlaps(result.rows, result.overlaps))

        thisLogger().info(
            "AgentWorktreeMonitor scanned ${result.rows.size} worktrees" +
                ", overlaps=${result.overlaps.size}" + if (light) " (auto)" else ""
        )
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val overlapPart = if (result.overlaps.isEmpty()) "" else " | 重叠 ${result.overlaps.size} 文件"
        statusLabel.text = "$projectDir | ${result.rows.size} 个 worktree$overlapPart | $time"
    }

    private fun flattenOverlaps(rows: List<WorktreeRow>, overlaps: List<OverlapItem>): List<OverlapRow> =
        overlaps.flatMap { item ->
            item.worktrees.map { info ->
                OverlapRow(item.filePath, info, rows.find { it.info.path == info.path }?.change, item.worktrees.size)
            }
        }

    private fun updateFileTable() {
        val row = worktreeList.selectedValue
        if (row == null || row.change == null) {
            fileTableModel.setRows(emptyList())
            return
        }
        fileTableModel.setRows(row.change.mergedFiles())
    }

    // ---------- Diff ----------

    private fun openSelectedFileDiff() {
        val wtRow = worktreeList.selectedValue ?: return
        val merged = fileTableModel.rowAt(fileTable.selectedRow) ?: return
        openDiffFor(wtRow, merged)
    }

    private fun openSelectedOverlapDiff() {
        val row = overlapTableModel.rowAt(overlapTable.selectedRow) ?: return
        val wtRow = WorktreeRow(row.info, row.change, null)
        val change = row.change?.files?.firstOrNull { it.path == row.filePath }
        openDiffFor(
            wtRow,
            MergedFileChange(row.filePath, committed = change == null, change = change, added = 0, deleted = 0)
        )
    }

    private fun openDiffFor(wtRow: WorktreeRow, merged: MergedFileChange) {
        val change = merged.change
        if (change != null && (change.status == FileStatus.DELETED || change.status == FileStatus.RENAMED)) {
            Messages.showInfoMessage(
                "删除/重命名文件的 Diff 对比将在后续版本支持",
                "Agent Worktree Monitor"
            )
            return
        }
        val worktreePath = Path.of(wtRow.info.path)
        if (change != null) {
            diffOpener.openDiff(worktreePath, change.path, change.untracked, leftRef = "HEAD")
        } else {
            // 仅存在于已提交变更中:左侧用基准分支版本
            val base = wtRow.change?.baseBranch
            if (base == null) {
                Messages.showInfoMessage("该 worktree 未配置基准分支,无法查看已提交变更 Diff", "Agent Worktree Monitor")
                return
            }
            diffOpener.openDiff(worktreePath, merged.path, untracked = false, leftRef = base)
        }
    }

    // ---------- 基准分支右键菜单 ----------

    private fun createBaseBranchMenu(): JPopupMenu {
        val menu = JPopupMenu()
        val selected = worktreeList.selectedValue
        if (selected == null) {
            menu.add(JMenuItem("(请先选中一个 worktree)").apply { isEnabled = false })
            return menu
        }
        val current = settings.baseBranchFor(selected.info.path)
        menu.add(JMenuItem(
            if (current != null) "当前基准分支:$current" else "未显式配置(默认按主 worktree 分支推断)"
        ).apply { isEnabled = false })

        val popup = menu
        val setItem = JMenuItem("设置基准分支…")
        setItem.addActionListener { showBaseBranchChooser(selected) }
        popup.add(setItem)
        if (current != null) {
            val clearItem = JMenuItem("清除配置(恢复默认推断)")
            clearItem.addActionListener {
                settings.setBaseBranch(selected.info.path, null)
                scheduleScan(light = false)
            }
            popup.add(clearItem)
        }
        return menu
    }

    private fun showBaseBranchChooser(row: WorktreeRow) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val branchList = scanner.listBranches(Path.of(row.info.path))
            SwingUtilities.invokeLater {
                if (branchList.isEmpty()) {
                    Messages.showInfoMessage("未获取到分支列表", "Agent Worktree Monitor")
                    return@invokeLater
                }
                val chosen = Messages.showEditableChooseDialog(
                    "为 ${row.info.branchDisplayName} 选择基准分支:",
                    "设置基准分支",
                    Messages.getQuestionIcon(),
                    branchList.toTypedArray(),
                    row.change?.baseBranch ?: branchList.firstOrNull(),
                    null
                )
                if (chosen != null) {
                    settings.setBaseBranch(row.info.path, chosen)
                    scheduleScan(light = false)
                }
            }
        }
    }

    // ---------- 自动刷新 ----------

    private fun restartAutoRefresh() {
        autoTimer?.stop()
        autoTimer = null
        if (!settings.autoRefreshEnabled) return
        val intervalSeconds = settings.autoRefreshIntervalSeconds.coerceAtLeast(5)
        autoTimer = Timer(intervalSeconds * 1000) { scheduleScan(light = true) }.apply { start() }
    }

    override fun dispose() {
        autoTimer?.stop()
        autoTimer = null
    }

    // ---------- 渲染与表格模型 ----------

    /** worktree 列表渲染:分支 + 基准 + 短 SHA/路径 + 未提交/已提交摘要 */
    private class WorktreeCellRenderer : JBLabel(), ListCellRenderer<WorktreeRow> {

        override fun getListCellRendererComponent(
            list: JList<out WorktreeRow>,
            value: WorktreeRow,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ): java.awt.Component {
            val sb = StringBuilder("<html>")
            sb.append("<b>").append(htmlEscape(value.info.branchDisplayName)).append("</b>")
            if (value.info.isMainWorktree) sb.append(" <font color='gray'>(主目录)</font>")
            if (value.info.isLocked) sb.append(" [locked]")
            val change = value.change
            if (change != null && change.baseBranch != null && !value.info.isMainWorktree) {
                sb.append(" <font color='gray' size='-1'>(base: ").append(htmlEscape(change.baseBranch)).append(")</font>")
            }
            sb.append("<br><font color='gray'>").append(value.info.headShort).append("  ")
                .append(htmlEscape(value.info.path)).append("</font>")
            sb.append("<br>")
            when {
                change == null -> sb.append("<font color='red'>").append(htmlEscape(value.error ?: "读取失败"))
                    .append("</font>")
                else -> {
                    if (!change.hasChanges) {
                        sb.append("<font color='gray'>未提交变更:无</font>")
                    } else {
                        sb.append("<font color='#2E7D32'>未提交 ").append(change.files.size).append(" 文件  +")
                            .append(change.addedLines).append(" / -").append(change.deletedLines).append("</font>")
                        if (change.untrackedCount > 0) {
                            sb.append(" <font color='gray'>(未跟踪 ").append(change.untrackedCount).append(")</font>")
                        }
                    }
                    if (change.hasCommittedChanges) {
                        sb.append("<br><font color='#1565C0'>已提交 ").append(change.commitCount).append(" commits  +")
                            .append(change.committedAdded).append(" / -").append(change.committedDeleted)
                            .append("</font>")
                    }
                }
            }
            sb.append("</html>")
            text = sb.toString()
            return this
        }

        private fun htmlEscape(s: String): String =
            s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    /** 变更文件表模型:文件 / 状态 / 来源 / +行 / -行(已提交与未提交合并视图) */
    private class FileTableModel : AbstractTableModel() {

        private var rows: List<MergedFileChange> = emptyList()

        fun setRows(files: List<MergedFileChange>) {
            rows = files
            fireTableDataChanged()
        }

        fun rowAt(index: Int): MergedFileChange? = rows.getOrNull(index)

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = 5

        override fun getColumnName(column: Int): String =
            arrayOf("文件", "状态", "来源", "+", "-")[column]

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.change?.oldPath?.let { "${row.change.oldPath} → ${row.path}" } ?: row.path
                1 -> row.change?.statusText ?: "已提交"
                2 -> when {
                    row.committed && row.change != null -> "已提交+未提交"
                    row.committed -> "已提交"
                    else -> "未提交"
                }
                3 -> row.added.toString()
                4 -> row.deleted.toString()
                else -> ""
            }
        }
    }

    /** 重叠表模型:文件 / 分支 / 状态 / 另有几方 */
    private class OverlapTableModel : AbstractTableModel() {

        private var rows: List<OverlapRow> = emptyList()

        fun setRows(rows: List<OverlapRow>) {
            this.rows = rows
            fireTableDataChanged()
        }

        fun rowAt(index: Int): OverlapRow? = rows.getOrNull(index)

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = 4

        override fun getColumnName(column: Int): String =
            arrayOf("文件", "涉及分支", "状态", "另有几方修改")[column]

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.filePath
                1 -> row.info.branchDisplayName
                2 -> when {
                    row.change?.files?.any { it.path == row.filePath } == true -> "有未提交修改"
                    row.change != null -> "已提交"
                    else -> "未知"
                }
                3 -> (row.totalOwners - 1).toString()
                else -> ""
            }
        }
    }
}
