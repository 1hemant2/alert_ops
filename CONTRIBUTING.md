# Contributing to ReplyTrail

ReplyTrail welcomes bug reports, documentation improvements, and focused code
contributions. The project is MIT-licensed and is still preparing its first
deployed release; see the [launch checklist](docs/product-launch-readiness.md).

## Report an issue

Use [GitHub issues](https://github.com/1hemant2/replytrail/issues) for ordinary bugs
and feature discussions. Include the version or commit, reproduction steps,
expected behavior, and actual behavior. Remove credentials, email action tokens,
personal data, and sensitive logs. Follow [SECURITY.md](SECURITY.md) for security
reports instead of publishing vulnerability details.

## Make a change

1. Discuss substantial behavior or architecture changes in an issue first.
2. Fork the repository and create a focused branch from `master`.
3. Follow [AGENTS.md](AGENTS.md), including the current-plan, naming, test, and
   changelog rules. Use the [README](README.md#run-locally) to set up locally.
4. Add focused tests for changed behavior and run the commands in
   [Development checks](README.md#development-checks). State which integration
   or deployed checks were skipped and why.
5. Open a pull request against `master`. Explain the problem, the change, and
   verification results. Keep unrelated work out of the pull request and keep
   backend, frontend, tests, and documentation in separate commits.

Preserve PostgreSQL as the durable source of truth, in-memory timers as wake-up
handles, and RabbitMQ as the ready-work queue. Never commit `.env` files, private
keys, provider credentials, or real user data. Contributions remain under the
repository's [MIT license](LICENSE.md).
