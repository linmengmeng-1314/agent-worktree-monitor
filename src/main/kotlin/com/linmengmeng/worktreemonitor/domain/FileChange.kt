package com.linmengmeng.worktreemonitor.domain

/** 文件变更类型(优先级:未跟踪 > 重命名 > 复制 > 新增 > 删除 > 类型变化 > 修改) */
enum class FileStatus {
    ADDED, MODIFIED, DELETED, RENAMED, COPIED, UNTRACKED, TYPE_CHANGED, UNKNOWN;

    val displayText: String
        get() = when (this) {
            ADDED -> "Added"
            MODIFIED -> "Modified"
            DELETED -> "Deleted"
            RENAMED -> "Renamed"
            COPIED -> "Copied"
            UNTRACKED -> "Untracked"
            TYPE_CHANGED -> "Type changed"
            UNKNOWN -> "Unknown"
        }
}

/** 状态展示文案:类型 + 层级标注(暂存/未暂存并存时标出) */
val FileChange.statusText: String
    get() {
        val layers = buildList {
            if (staged) add("暂存")
            if (unstaged) add("未暂存")
            if (untracked) add("未跟踪")
        }
        return if (layers.size > 1) "${status.displayText}(${layers.joinToString("+")})" else status.displayText
    }

/**
 * 一个 worktree 内的单文件变更,来自 `git status --porcelain=v1 -z` 解析结果。
 * x 为暂存区状态列,y 为工作区状态列,取值见 git status 文档(空格表示无该层变更)。
 */
data class FileChange(
    /** 相对 worktree 根的路径;重命名/复制时为新路径 */
    val path: String,
    /** 重命名/复制时的原路径,其余为 null */
    val oldPath: String?,
    val x: Char,
    val y: Char
) {
    val untracked: Boolean get() = x == '?'
    val staged: Boolean get() = x != ' ' && x != '?'
    val unstaged: Boolean get() = y != ' ' && y != '?' && y != '!'

    val status: FileStatus
        get() = when {
            x == '?' -> FileStatus.UNTRACKED
            x == 'R' || y == 'R' -> FileStatus.RENAMED
            x == 'C' || y == 'C' -> FileStatus.COPIED
            x == 'A' -> FileStatus.ADDED
            x == 'D' || y == 'D' -> FileStatus.DELETED
            x == 'T' || y == 'T' -> FileStatus.TYPE_CHANGED
            x == 'M' || y == 'M' -> FileStatus.MODIFIED
            else -> FileStatus.UNKNOWN
        }
}

/**
 * 一个 worktree 的变更汇总:未提交 + 已提交(相对基准分支)两层。
 * 行数口径:未提交 = 工作区 vs HEAD;已提交 = HEAD vs 基准分叉点(base...HEAD),两层相加不重复。
 */
data class WorktreeChange(
    val worktree: WorktreeInfo,
    val files: List<FileChange>,
    val addedLines: Int,
    val deletedLines: Int,
    /** path -> (added, deleted)。已跟踪文件来自 numstat,未跟踪文件为 (行数, 0) */
    val lineStats: Map<String, Pair<Int, Int>> = emptyMap(),
    /** 实际使用的基准分支(显式配置或推断);null = 未配置,不统计已提交变更 */
    val baseBranch: String? = null,
    /** 已提交变更文件:base...HEAD numstat(rename 已展开为新路径) */
    val committedFiles: Map<String, Pair<Int, Int>> = emptyMap(),
    val committedAdded: Int = 0,
    val committedDeleted: Int = 0,
    val commitCount: Int = 0
) {
    val hasChanges: Boolean get() = files.isNotEmpty()
    val untrackedCount: Int get() = files.count { it.untracked }
    val hasCommittedChanges: Boolean get() = committedFiles.isNotEmpty() || commitCount > 0
}

/**
 * 展示层模型:已提交与未提交变更按文件合并后的单行。
 * committed=true 且 change=null 表示仅存在于已提交变更中的文件。
 */
data class MergedFileChange(
    val path: String,
    val committed: Boolean,
    val change: FileChange?,
    val added: Int,
    val deleted: Int
)

/** 合并已提交 + 未提交两层为按文件的单一视图(行数两层相加,不重复累计) */
fun WorktreeChange.mergedFiles(): List<MergedFileChange> {
    val merged = LinkedHashMap<String, MergedFileChange>()
    for ((path, stat) in committedFiles) {
        merged[path] = MergedFileChange(path, committed = true, change = null, added = stat.first, deleted = stat.second)
    }
    for (f in files) {
        val stat = lineStats[f.path] ?: (0 to 0)
        val existing = merged[f.path]
        merged[f.path] = if (existing != null) {
            existing.copy(
                change = f,
                added = existing.added + stat.first,
                deleted = existing.deleted + stat.second
            )
        } else {
            MergedFileChange(f.path, committed = false, change = f, added = stat.first, deleted = stat.second)
        }
    }
    return merged.values.toList()
}
