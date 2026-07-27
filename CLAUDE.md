# Repository Instructions

## Single-branch policy

- `main` is the repository's only permanent local or remote branch.
- Do not create or retain `develop`, release, feature, or other long-lived
  branches. Releases use semantic-version tags and GitHub Releases, not release
  branches.
- Keep `main` protected. Never force-push it or delete it.
- When branch protection requires a pull request, use one short-lived
  `codex/<description>` branch solely to deliver that pull request. Push it only
  when the pull request will be opened and completed as part of the same task.
- After the pull request merges, delete its remote branch, delete its local
  branch, prune stale remote references, switch back to `main`, and update local
  `main` to match `origin/main`.
- Finish every completed task with `git branch` showing only `main` and
  `git branch -r` showing only `origin/main` (apart from the symbolic
  `origin/HEAD` reference).
- If a required check or external decision prevents merging, report the
  temporary branch explicitly instead of presenting the single-branch policy as
  satisfied.

Preserve unrelated user work while applying this policy. Before removing a
branch, verify that its unique commits are merged into `main` or otherwise
preserved.
