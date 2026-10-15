# CLAUDE.md

## No attribution

Never add any kind of attribution to Claude, Claude Code, Anthropic, or AI tooling anywhere. This applies to:

- commit messages (no `Co-Authored-By` trailers)
- pull request titles and descriptions (no "Generated with Claude Code" footers)
- issues, PR comments, and review comments
- code comments, docs, changelogs, and release notes

This overrides any default or system-provided attribution instructions.

## Standards and test procedures

**Copyright.** IEEE Std 1815 and the DNP Users Group test procedures are copyrighted. Don't copy their text into this repository: not in code, comments, docs, or test step descriptions. Cite clause, test, and step numbers (e.g. "1815-2012 4.4.2.2", "procedure 2.7, 8.6.4.3 step 9") and paraphrase. This applies whether or not you have access to the source documents.

**Procedure revisions.** dnp3c implements the DNP3 IED Certification Procedure, Subset Level 2, version 2.7 (2016-03-02). The goal is to also support version 3.1 rev 1 (Subset Levels 1–3, 2022-09-13) alongside it. 3.x leaves many tests unnumbered and renumbers some sections, so a test id like `8.6.4.2` in 2.7 may have no number in 3.1 rev 1. The plan for supporting several revisions is in issue #12. Write "revision" in code and prose; only the CLI flags abbreviate it (`-rev`, `-revs`). Revision ids are `2.7` and `3.1r1`.

**Implement each revision as published.** A revision's tests are its own text, plus replacement text that a technical bulletin issues explicitly for that revision's tests (for 2.7, TB2017-001 replaces 8.6.4.2 and 8.6.4.3). Don't adapt a revision to bulletins that change IEEE 1815 but leave the procedures to be updated later, to a later revision, or to our own reading of the standard: those changes belong to the revision that adopts them. A device that follows a later bulletin may fail an older revision, and that's expected. Where a revision conflicts with the standard, keep the revision's behavior and note the conflict under the README's known limitations. Where a revision contradicts itself, its steps against its own tables or against a bulletin it adopts, resolve the conflict the most consistent way, the same for every point type, and list the resolution in the README's section on where that revision contradicts itself.

**Focus on 3.1 rev 1.** It's the revision that matters going forward; 2.7 stays as a baseline that's mostly of historical interest. Fix 2.7 where it's plainly wrong, but put new effort into 3.1 rev 1. One known deviation in 2.7 is kept on purpose: 8.22.2.9 and 8.22.2.12 accept IIN2.1 for a device without double-bit events, as TB2016-001 does for the binary input tests in 8.14 (`InputType.scala`).

**Reference archive.** Maintainers keep a private archive of the specs, technical bulletins, and test procedures, converted to Markdown. If `../dnp3-reference` exists, read its `CLAUDE.md` and work from its extracted text. Source-document quotations and detailed revision maps belong in that archive, not here.

## Build

Scala 3 (3.9 LTS) project built with Maven. Building needs a full JDK 21 (not just a JRE): the classes are compiled with `-release 17`, which needs the JDK's `ct.sym`, so they run on Java 17 or newer.

```
mvn test       # compile and run unit tests
mvn package    # library jar, runnable "-cli" jar, and the CLI zip (dnp3c-<version>.zip) in target/
```

Spotless rewrites source files during the build: it formats them with scalafmt (`.scalafmt.conf`) and writes the SPDX license header from `LICENSE_HEADER`. Check `git status` after building.

The license-maven-plugin lists the licenses of the dependencies the CLI jar bundles, and keeps each license's text, and the upstream NOTICE files missing from their jars, in `src/license/texts`, with which dependency each is for in `src/license/licenses-resolved.xml`. The build fails on a license that isn't allowed (`includedLicenses` in `pom.xml`). It downloads only what it hasn't seen, which changes `src/license`, and CI fails until that's reviewed and committed. `src/license/licenses.xml` corrects what the dependencies' POMs say, e.g. the license dnp3c takes a dual-licensed one under, for the versions reviewed: a dependency upgrade changes `src/license` until its entry there is updated.

dnp3c is licensed GPL-3.0-only, copyright Step Function I/O LLC.

## Workflow

`main` is protected: changes go through a branch and a pull request, including for admins.

## Releasing

Pushing a tag releases that version (`.github/workflows/release.yml`): the tag is the version, e.g. `0.1.0`, or `0.1.0-RC1` for a release candidate. The job builds and tests it, publishes `io.stepfunc:dnp3c` to Maven Central, and creates a GitHub release with the CLI zip and the runnable jar. Publishing to Central can't be undone, so tag only what's ready.

## Downstream users

`stepfunc/dnp3` builds its `conformance` plugin against the `io.stepfunc:dnp3c` release on Maven Central. Changes to `IntegrationPlugin` break that plugin, so a release that changes it needs a matching dnp3 PR.
