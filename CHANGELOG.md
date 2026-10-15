# Changelog

## 0.2.0

- **DNP3 IED Certification Procedure 3.1 rev 1**, subset levels 1 to 3, alongside 2.7. Choose the revision with `-rev`; `-revs` lists them.
- **Serial and UDP**, as well as TCP, including 3.1 rev 1's network, multi-drop and collision avoidance tests.
- **Exclusions:** leave a procedure out of a run with a reason, e.g. for a known gap in the device. The report shows it as not run, apart from skipped procedures, with its reason.
- **Reports** say why each procedure was skipped, and name the revision and subset level.
- The runnable jar and the CLI zip list the licenses of the dependencies they bundle.

### Upgrading from 0.1.0

- `-rev` is required, e.g. `-rev 2.7`.
- `outstation.subset.level` is required in the configuration.
- Results are kept by revision and level, e.g. `results/2.7/L2/`.
- Some configuration keys were renamed or removed; an old key is reported as an unknown option. `-write` writes a file with every current option.
- Library: `Harness` takes the revision to run, e.g. `new Harness(options, plugin, Revisions.v2_7)`, and so does `Harness.writeReport(options, revision)`. `ProcedureResult` has a third field, `notes`.
- Integration plugins: `IntegrationPlugin`'s per-type methods take the kind of point, and runs can report a procedure as `ProcedureOutcome.Excluded`. See [Automating the tests](https://github.com/stepfunc/dnp3c/blob/0.2.0/docs/automation.md).

## 0.1.0

The first release: the DNP3 IED Certification Procedure, Subset Level 2, version 2.7, over TCP, with a command-line tool, an HTML report, packet capture, and a library for automated runs through an integration plugin.
