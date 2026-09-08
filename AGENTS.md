# Local development process cleanup

- When starting this project or the related `admin-web` or `mobile-app` processes for API tests or frontend/backend integration testing, treat those processes as temporary test processes.
- After the test or integration run finishes, stop every related frontend/backend process started for that run and verify that each corresponding port has been released before handing control back to the user.
- Before stopping a process, verify its ownership using at least one concrete signal: the recorded PID, the command line, the working directory, or the listening port. Only stop processes confirmed to belong to this project and this test run.
- Do not broadly terminate all `node`, `java`, or other runtime processes. Never use a process-name-only kill when unrelated user processes may be affected.
- Record the PIDs and ports when starting temporary services so cleanup can target exact processes. If a process has already exited, still verify the expected port is not listening.
- If a port remains occupied after targeted cleanup, inspect the owning PID and command line, report the conflict, and do not kill an unrelated process.

# Git collaboration

- Before starting each task, fetch and pull the remote `dev` branch from `origin`; treat `origin/dev` as the source of truth.
- Resolve conflicts in favor of the remote `dev` branch. Do not push while conflicts or failed tests remain.
- After the task is complete and all relevant builds/tests pass, verify the current branch and clean/expected worktree before pushing only to `origin/dev`.
- If tests fail or conflicts remain unresolved, do not push and report the blocking reason.
