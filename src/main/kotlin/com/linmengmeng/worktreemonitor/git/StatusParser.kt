package com.linmengmeng.worktreemonitor.git

import com.linmengmeng.worktreemonitor.domain.FileChange

/**
 * 解析 `git status --porcelain=v1 -z` 输出。
 *
 * -z 模式每条记录以 NUL 终止,格式 `XY <path>`。
 * 注意坑:重命名/复制记录在 -z 模式下是【新路径在前、原路径在后】两个连续 NUL 记录,
 * 与非 -z 模式的 `old -> new` 展示顺序相反。
 */
object StatusParser {

    fun parse(output: ByteArray): List<FileChange> {
        val tokens = String(output, Charsets.UTF_8)
            .split('\u0000')
            .map { it.trimEnd('\n', '\r') }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<FileChange>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.length < 4) {
                i++
                continue
            }
            val x = token[0]
            val y = token[1]
            val path = token.substring(3)
            if ((x == 'R' || x == 'C') && i + 1 < tokens.size) {
                val fromPath = tokens[i + 1]
                result += FileChange(path, fromPath, x, y)
                i += 2
            } else {
                result += FileChange(path, null, x, y)
                i++
            }
        }
        return result
    }
}

/**
 * 解析 `git diff --numstat HEAD` 输出(文本,每行 `added\tdeleted\tpath`)。
 * 二进制文件行数为 `-`,跳过;重命名路径 `{old => new}` 展开为完整新路径。
 */
object NumstatParser {

    private val RENAME_PATTERN = Regex("^(.*)\\{(.*) => (.*)\\}(.*)$")

    fun parse(text: String): Map<String, Pair<Int, Int>> {
        val map = mutableMapOf<String, Pair<Int, Int>>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size < 3) continue
            val added = parts[0].toIntOrNull() ?: continue
            val deleted = parts[1].toIntOrNull() ?: continue
            val rawPath = parts.drop(2).joinToString("\t")
            val renameMatch = RENAME_PATTERN.matchEntire(rawPath)
            val finalPath = if (renameMatch != null) {
                renameMatch.groupValues[1] + renameMatch.groupValues[3] + renameMatch.groupValues[4]
            } else {
                rawPath
            }
            map[finalPath] = added to deleted
        }
        return map
    }
}
