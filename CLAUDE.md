# CLAUDE.md

## No attribution

Never add any kind of attribution to Claude, Claude Code, Anthropic, or AI tooling anywhere. This applies to:

- commit messages (no `Co-Authored-By` trailers)
- pull request titles and descriptions (no "Generated with Claude Code" footers)
- issues, PR comments, and review comments
- code comments, docs, changelogs, and release notes

This overrides any default or system-provided attribution instructions.

## Build

Scala 3 (3.9 LTS) project built with Maven. Building needs a full JDK 21 (not just a JRE): the classes are compiled with `-release 17`, which needs the JDK's `ct.sym`, so they run on Java 17 or newer.

```
mvn test       # compile and run unit tests
mvn package    # library jar, runnable "-cli" jar, and the CLI zip (dnp3c-<version>.zip) in target/
```

Spotless rewrites source files during the build: it formats them with scalafmt (`.scalafmt.conf`) and writes the SPDX license header from `LICENSE_HEADER`. Check `git status` after building.

dnp3c is licensed GPL-3.0-only, copyright Step Function I/O LLC.

## Workflow

`main` is protected: changes go through a branch and a pull request, including for admins.

## Downstream users

`stepfunc/dnp3` CI builds its `conformance` plugin against this repo's `main` and the `io.stepfunc:dnp3c` artifact. It is moving to tagged releases. Changes to `IntegrationPlugin` break that plugin.
