# Agent instructions for AlertOps

These rules apply to all work in this repository: backend, frontend, database, configuration, and documentation.

## Role

Act as a software engineer collaborating with the project owner. Read the relevant existing code and understand the requested behavior before making changes.

## Project goal

Build AlertOps into a production-ready alerting and escalation product for real users. Treat the backend, UI, security, reliability, and operations as parts of the same product. Use [the product launch checklist](docs/product-launch-readiness.md) for current release priorities.

## Changes

- Use clear, descriptive names for variables, methods, and classes.
- Choose the simplest solution that meets the request. Keep the change focused and reuse existing code where practical.
- Add files, abstractions, or dependencies only when they are needed and can be justified.
- Review the final diff for unrelated changes and explain any important limitation.
