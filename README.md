# Agent Worktree Monitor

An IntelliJ IDEA plugin that monitors code changes across multiple Git worktrees for parallel AI-agent development.

When several AI coding agents (Claude Code, Codex, OpenCode, ...) work in parallel, each in its own Git worktree, it is hard to see what every agent is changing. This plugin gives you a single review entry point: it automatically discovers all worktrees of the current repository and shows branches, committed and uncommitted changes, line statistics, and files modified by multiple worktrees — with native IDEA diffs on double-click.

**Read-only and local-first**: the plugin never performs git write operations, makes no network requests, and collects no telemetry.

[中文说明](README.zh-CN.md)

## Features

- Automatic discovery of all worktrees of the repository (detached, locked, and paths with spaces or non-ASCII characters supported)
- Overview per worktree: branch, short HEAD SHA, number of changed files, added/deleted lines, untracked count
- **Base branch & committed changes**: configure a base branch per worktree (right-click; defaults to the main worktree branch), statistics from `base...HEAD` are merged with uncommitted changes per file — no double counting
- File list: staged / unstaged / untracked layers plus "committed / uncommitted" source; renames shown as `new ← old`
- **Cross-worktree overlap detection**: files modified by two or more worktrees are listed with all owners (Overlap tab); double-click opens the diff for that worktree
- Double-click a file to open the native IDEA diff (uncommitted files vs HEAD, committed-only files vs the base branch)
- **Auto refresh**: 5/10/30/60 s polling (default 10 s), lightweight signature comparison skips detailed scans when nothing changed; refreshes on window activation; can be disabled

## Requirements

- IntelliJ IDEA 2026.2+ (build 262+), Community or Ultimate
- Git ≥ 2.19 (requires `--porcelain -z` support)

## Install

Download `agent-worktree-monitor-<version>.zip` from [Releases](../../releases) → Settings → Plugins → ⚙ → Install Plugin from Disk → select the zip → restart. Open any Git project; the "Agent Worktree Monitor" tool window appears on the right.

## Privacy

Local-first. All data is obtained by running read-only `git` commands locally. No telemetry, no analytics, no network requests.

## Development

```bash
# Build and test (requires JDK 21)
./gradlew build

# Launch a sandbox IDE to try the plugin
./gradlew runIde

# Package the installable zip: build/distributions/*.zip
./gradlew buildPlugin

# Verify compatibility (plugin verifier)
./gradlew runPluginVerifier
```

To reuse an installed IDEA instead of downloading a platform dependency, set `LOCAL_IDE_PATH` (or pass `-PlocalIdePath=<idea path>`).

### Test repository

`scripts/make_test_repo.py` creates a test repository with 4 worktrees covering all validation scenarios (non-ASCII/space paths, detached, locked, renames, staged+unstaged, untracked, cross-worktree overlap):

```bash
python scripts/make_test_repo.py --base D:/tmp
```

## Feedback

Issues and suggestions: [GitHub Issues](../../issues)

## License

[Apache-2.0](LICENSE) · [EULA](EULA.md) · [Changelog](CHANGELOG.md)
