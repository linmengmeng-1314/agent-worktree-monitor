# Changelog

## 0.2.2

- Marketplace readiness: English-first plugin description, vendor contact info, 40×40 plugin icon
- Git process timeout (15 s) to prevent leaked processes; atomic refresh guard
- Documentation: English README, Chinese README (README.zh-CN.md), EULA, this changelog
- CI (GitHub Actions): build + test on push

## 0.2.1

- File list shows the file name first with the directory as a gray suffix; full relative path on hover (matches the built-in Git tool window); applied to the Overlap tab as well
- Renamed files display as `new name ← old path`

## 0.2.0

- Base branch per worktree (right-click to configure; defaults to the main worktree branch), committed change statistics via `base...HEAD`, merged with uncommitted changes per file without double counting
- Cross-worktree overlap detection (files modified by two or more worktrees) with an Overlap tab
- Auto refresh (5/10/30/60 s, signature-based, refresh on window activation, can be disabled)
- Distinct tool window icon

## 0.1.0

- Initial release: worktree discovery, overview panel, change file list, native diff on double-click, manual refresh
