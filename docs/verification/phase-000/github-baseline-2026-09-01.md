# Phase 000 GitHub baseline

Captured 2026-09-01 during P000-TASK-003 from the authenticated `EnVisione` account. The commands were read only.

## Repository and protections

The repository is public, active, and has Issues, Projects, and the wiki enabled. The default branch is `1.20.1`. Merge commits are enabled. Squash and rebase merges are disabled. Dependabot security updates and secret scanning push protection are enabled. The repository reports zero code scanning alerts and zero secret scanning alerts.

The active branch ruleset `mcenvision main protection` applies to `refs/heads/1.20.1`. It blocks deletion and non fast forward updates, requires signed commits, requires pull request conversation resolution, and permits zero required approvals. The active release tag ruleset protects `refs/tags/v*` and `refs/tags/phase-*`. The legacy branch protection endpoint reports 404 for both `1.20.1` and `1.21.1`, so protection is provided by rulesets rather than the legacy branch protection API.

The main ruleset does not currently list required status checks. The tag ruleset allows an organization administrator bypass. These are baseline findings for later reconciliation against the active plan.

## Pull requests and actions

One pull request is open. Pull request 28 is the Dependabot safe updates group targeting `1.20.1`, and its merge state is clean. Recent quality and Dependabot runs are successful. An older Dependabot run on `1.20.1` failed on 2026-08-13 and needs normal dependency workflow review.

The repository has active `quality` and `release validation` workflows. GitHub also reports a dynamic `Copilot` workflow and the dynamic `Dependabot Updates` workflow. The dynamic Copilot entry is a baseline drift finding because the repository policy requires automatic Copilot review to remain disabled.

## Dependencies and security

The checked in Dependabot configuration covers GitHub Actions and Gradle at the repository root, on a weekly schedule, grouping minor and patch updates. GitHub reports 26 Dependabot alerts. Twenty five are dismissed historical alerts. Alert 26 for `org.apache.logging.log4j:log4j-api` remains open at medium severity and must be reviewed before the release gate.

There are no code scanning or secret scanning alerts. Security analysis reports secret scanning and push protection enabled. Dependency graph and alert state were readable through the authenticated API.

## Roadmap, milestones, releases, and wiki

Organization project 6 is titled `futureshops roadmap` and contains 16 items. The plan calls for one repository linked project named `<repository> roadmap`, so the title and linkage require later reconciliation. Existing milestones are `phase 3 visitor vertical slice`, `phase 4 administrator offer builder`, `2.2.1 maintenance`, and `3.0 beta maintenance`. The repository has no GitHub releases. The wiki is enabled.

Labels include the standard bug, enhancement, documentation, dependency, security, lifecycle, priority, loader, Java, and GitHub Actions labels required by the plan.

## Evidence and follow up

The exact issue packets for issues 22, 25, 32, 33, and 34 are recorded in `issue-snapshots-2026-09-01.md`. Findings above are observations only. P000-TASK-003 does not change repository settings, issues, pull requests, workflows, dependencies, or releases.
