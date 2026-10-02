# Retired workflow source history — 2026-10-03

Only `main` is the active workflow. Fable, Antigravity, Astra and Claude branch
names are recorded in [manifest.json](manifest.json). Their merged commits remain
in main's history; commits unique to retired branches are preserved in
[history.gitbundle](history.gitbundle), including final local source snapshots.
These alternatives are historical evidence, not new releases or verified parity.

## Restore an alternative

Use a full clone (not a shallow clone) of this repository. Run from its root:

```sh
git bundle verify archive/retired-workflows/2026-10-03/history.gitbundle
git bundle list-heads archive/retired-workflows/2026-10-03/history.gitbundle
git fetch archive/retired-workflows/2026-10-03/history.gitbundle 'refs/heads/*:refs/remotes/retired/*'
```

Then use the exact commit from the manifest with `git show <commit>` or
`git worktree add ../historical-review <commit>`. Merged branches may not appear
in the bundle because their commits already exist on main. The bundle requires
main's historical base; `git fetch --unshallow` first if necessary. No tags or
Maven releases were created by consolidation. Private captures and transcripts
are retained on the owner's PC, outside GitHub.
