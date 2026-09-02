# Phase 000 provenance capture

This record captures `P000-TASK-001` for the FutureShops defect closure plan. It is evidence for the planning branch only and is not a product or release artifact.

## Identity and baseline

| Field | Observed value |
| --- | --- |
| Repository root | `/mnt/hermes/projects/FutureShops` |
| Remote | `origin`, `https://github.com/MCEnvision/FutureShops.git` |
| Checkout branch | `envy/polish_plan` |
| Checkout commit | `d978a1f79d5c50efee5d91ea8cace232ac542116` |
| Local default branch | `1.20.1` at `c6709e12ca7084ee068b2497a577b8d47c12f6fd` |
| Local remote tracking ref | `origin/1.20.1` at `c6709e12ca7084ee068b2497a577b8d47c12f6fd` |
| Live remote default head | `1.20.1` at `c6709e12ca7084ee068b2497a577b8d47c12f6fd` |
| Secondary support ref | `origin/1.21.1` at `247d8f6842bfa1f586e5b18a9aab67cabd3db89f` |
| Issue 22 candidate | `envy/issue_22_neoforge` at `bfba91f7b0c51b03d07117c4f1851c38a98f6186` |
| Issue 22 ancestry | Candidate contains `origin/1.21.1`, with `0` commits behind and `1` commit ahead |
| Authenticated Git identity | `EnVisione`, commit identity `EnVy <contact.enviouse@gmail.com>` |

The live remote head was read with `git ls-remote` at `2026-09-01T23:28:23-05:00` and rechecked at `2026-09-01T23:28:24-05:00`. The repository baseline is established because the local default branch, its remote tracking ref, and the live remote head match exactly.

## Before and after fetch

The before capture at `2026-09-01T23:28:23-05:00` recorded checkout `d978a1f79d5c50efee5d91ea8cace232ac542116`, default branch `1.20.1` at `c6709e12ca7084ee068b2497a577b8d47c12f6fd`, the untracked files listed below, and no tracked or staged diff. The scoped fetch at `2026-09-01T23:28:24-05:00` read `1.20.1`, `1.21.1`, and `envy/issue_22_neoforge` without checkout, merge, rebase, pruning, or force. The after capture retained the same checkout, support object IDs, untracked files, and empty tracked and staged diffs.

Pre-existing untracked files were preserved and not staged:

* `.github/pull_request_template.md`
* `.github/workflows/ci.yml`
* `.github/workflows/codeql.yml`
* `run-data/`

## Legacy plan preservation

The required legacy plans were hashed before the fetch and again after it. Both values are unchanged.

| File | SHA-256 |
| --- | --- |
| `FutureShops3-0Plan.MD` | `bb8d985a265c72d42d3ce39b05b0e4ab516549da1e5607fd1ed853f52685ac90` |
| `FutureShops3-1TradeOffersPlan.MD` | `d3ebf8948bf68efea34e81feacb1ab0c301efe3799372c740eb7504dd6042f64` |

## Result

`P000-TASK-001` passes. The authoritative repository and remote are identified, the default branch is synchronized with its live remote head, support-line and issue 22 refs are pinned, pre-existing work is isolated, and both historical plans remain byte-for-byte unchanged. No product source, configuration, release metadata, issue state, or unrelated file was changed by this task.
