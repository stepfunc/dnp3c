# Changelog

## 0.3.0

- **Technical bulletins:** `outstation.bulletins` lists the bulletins a device implements, where they change what a revision's steps check; none by default. The first is TB2017-003a: with it, 2.7's binary and double-bit event tests with time (8.14.2.9 to 8.14.2.11, 8.22.2.9 to 8.22.2.11) accept events recorded while the clock is unsynchronized with relative time after g51v2, and require it.
- **Clock state:** integration plugins can tell dnp3c whether the device's clock is synchronized (`IntegrationPlugin.timeQuality`), so 3.1 rev 1's binary and double-bit event tests, and 2.7's with TB2017-003a, check each event's variation against the state it was recorded in. A batch of events recorded while the clock changed may be reported each way in one response.

### Upgrading from 0.2.0

- Integration plugins: `IntegrationPlugin.timeQuality` has a default, so a plugin keeps compiling; without it, the checks accept either way of reporting the events, as before.

## 0.2.0

- **DNP3 IED Certification Procedure 3.1 rev 1**, subset levels 1 to 3, alongside 2.7. Choose the revision with `-rev`; `-revs` lists them.
- **Serial and UDP**, as well as TCP, including 3.1 rev 1's network, multi-drop and collision avoidance tests.
- **Exclusions:** leave a procedure out of a run with a reason, e.g. for a known gap in the device. The report shows it as not run, apart from skipped procedures, with its reason.
- **Reports** say why each procedure was skipped, flag the procedures that pass with warnings, where the device should probably change something, and name the revision and subset level.
- **Timing tolerance:** `test.timingToleranceMs` (500 ms by default) sets how far the times dnp3c measures may be off, e.g. for a busy machine or a slow network. It replaces two fixed allowances, of 500 ms for timed intervals and 200 ms past each timeout, so the steps that expect no message now wait 300 ms longer by default.
- The runnable jar and the CLI zip list the licenses of the dependencies they bundle.

### Upgrading from 0.1.0

- `-rev` is required, e.g. `-rev 2.7`.
- `outstation.subset.level` is required in the configuration.
- Results are kept by revision and level, e.g. `results/2.7/L2/`.
- Some configuration keys were renamed or removed; an old key is reported as an unknown option. `-write` writes a file with every current option.
- Library: `Harness` takes the revision to run, e.g. `new Harness(options, plugin, Revisions.v2_7)`, and so does `Harness.writeReport(options, revision)`. `ProcedureResult` has two more fields, `notes` and `warnings`.
- Integration plugins: `IntegrationPlugin`'s per-type methods take the kind of point, and runs can report a procedure as `ProcedureOutcome.Excluded`. See [Automating the tests](https://github.com/stepfunc/dnp3c/blob/0.2.0/docs/automation.md).

## 0.1.0

The first release: the DNP3 IED Certification Procedure, Subset Level 2, version 2.7, over TCP, with a command-line tool, an HTML report, packet capture, and a library for automated runs through an integration plugin.
