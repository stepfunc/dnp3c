# Automating the tests

dnp3c is also a library. The command-line tool prompts you for the steps it can't perform over DNP3, such as cycling power, generating events, or checking that an output operated. For automated testing, e.g. on every commit in CI, you write an integration plugin that performs those steps on your device instead. dnp3c then runs the procedures with no one at the keyboard.

[Testing a device by hand](manual-testing.md) covers the configuration, the results and the report. They're the same when automated.

## How it fits together

Your program loads a configuration file, creates a `Harness` with your plugin and the revision of the certification procedure to run, and runs procedures:

```scala
import java.nio.file.Paths
import scala.util.control.NonFatal

import io.stepfunc.dnp3c.conformance.{ Harness, Revisions }
import io.stepfunc.dnp3c.conformance.api.Options

object Main {
  def main(args: Array[String]): Unit = {
    val harness = new Harness(Options.load(Paths.get("device.ini")), new MyDevicePlugin(), Revisions.v2_7)

    // write the report however the run ends, without hiding why it ended
    var reported = true
    val summary =
      try harness.run("all")
      finally
        try harness.writeReport()
        catch {
          case NonFatal(ex) =>
            System.err.println(s"Failed to write the report: ${ex.getMessage}")
            reported = false
        }

    summary.results.foreach(r => println(s"${r.id}: ${r.outcome}"))
    if (!summary.succeeded || !reported) sys.exit(1)
  }
}
```

- `Options.load` parses and checks the whole file, and throws a `ConfigurationException` listing every problem.
- The revision decides which procedures exist and what their ids mean. `Revisions.list` holds every revision dnp3c has, and `Revisions.find("2.7")` looks one up by id. There's no default.
- The configuration must set `outstation.subset.level`, the subset level the device is configured for, to a level the revision tests: level 2 for 2.7. `run` and `writeReport` throw a `ConfigurationException` before doing anything if it doesn't. `run` also does if the device lacks something a procedure needs at a level where the revision makes that procedure mandatory, rather than skipping it.
- `run` takes a procedure or section id of the revision, e.g. `"8.6.5.4"` or `"8.6"`, or `"all"`, and throws an `IllegalArgumentException` for an unknown one before anything runs. It runs the procedures in order and stops at the first that fails. It returns a `RunSummary` with each procedure's outcome (`Passed`, `Failed(message)` or `Skipped(reason)`) and the procedures it didn't reach.
- Results go in the folder for the revision and subset level in the output directory, e.g. `results/2.7/L2/`, so runs of different revisions or levels never mix.
- `writeReport` writes `report.html` in that folder from every result there, from this run and earlier ones, and returns its path. `Harness.writeReport(options, revision)` does the same without a plugin.

For each procedure, the harness:

1. calls `startProcedure` on the plugin;
2. connects to the DUT over TCP and runs the steps, calling the plugin for each step it can't perform itself;
3. calls `endProcedure`, closes the connection, and writes the procedure's results.

A procedure that won't run is skipped before any of that: one that isn't implemented, doesn't apply at the subset level, or needs a feature the device lacks. It gets a log and a result saying why, but the harness doesn't connect to the DUT or call the plugin, not even `startProcedure` or `endProcedure`.

The plugin runs in the same JVM as dnp3c. It can control a device elsewhere, e.g. through a test fixture, an API or a simulator, or it can host the device itself. A software outstation can be started in `startProcedure` and stopped in `endProcedure`, so each procedure starts from a known state.

### When things fail

- **A procedure fails** when one of its steps or validations does, including a plugin method called during a step that throws. Its outcome is `Failed(message)`, and the run stops there.
- **The harness or the plugin fails around a procedure**, e.g. `startProcedure` or `endProcedure` throws, the capture can't be opened, or the log can't be written. `run` throws a `HarnessExecutionException` naming the procedure, with the results so far in `partial`. The procedure gets no result file.
- **An interruption** propagates as it is, after the procedure's cleanup.
- **Writing the report** can fail on its own; `writeReport` throws.

### Running more than one

A harness can be reused for one run after another, but not for runs that overlap, and a report shouldn't be written while a run on the same output directory is in progress. Harnesses that run at the same time need their own devices, plugins and output directories, and plugins that don't prompt, since prompts read the process's standard input.

The harness never exits the JVM, but it does log to the console, and `DefaultIntegrationPlugin`'s methods prompt.

### Using the CLI with your plugin

To get the whole command-line tool, with its commands and options, but with your plugin performing the steps, call `Main.run`:

```scala
io.stepfunc.dnp3c.conformance.Main.run(args, new MyDevicePlugin())
```

`Main.run` exits the JVM with 0 if every procedure passed or was skipped, and nonzero otherwise.

## Dependency

Plugins depend on the library jar, `io.stepfunc:dnp3c`:

```xml
<dependency>
    <groupId>io.stepfunc</groupId>
    <artifactId>dnp3c</artifactId>
    <version>0.1.0</version>
</dependency>
```

Until it's published, install it into your local Maven repository from a checkout of this repository with `mvn install`, and depend on the version that installs, `0.1.0-SNAPSHOT`.

dnp3c is written in Scala 3, and so is `IntegrationPlugin`. Writing the plugin in Scala is the easiest path, since its methods take Scala collections.

The supported API is `Harness`, `Revision` and `Revisions`, the configuration, results and exceptions in `io.stepfunc.dnp3c.conformance.api`, `IntegrationPlugin` and `DefaultIntegrationPlugin`, and the types their methods use, such as `PluginReporter`. Other public types, such as `TestDriver`, `TestReporter` and `ProcedureReporter`, are implementation details that can change between versions.

## Writing the plugin

`IntegrationPlugin` (`io.stepfunc.dnp3c.dnp3`) has two kinds of methods:

- **Actions** change the device: `cyclePower`, `generateInputChanges`, `enableUnsolicitedResponse`, `setMasterAddress`, and so on.
- **Verifications** check what the device did, or what it reported: `checkBinaryOutputOperate`, `verifyInputCurrentValues`, `verifyTimestamp`, and so on. They receive what dnp3c parsed from the device's responses, such as the points and their values, to compare with what the device actually holds.

Verifications return `Option[DeferredCheck]`, and `generateUnsolicitedEvents` returns an `ActionResult`: an automated plugin checks or acts immediately and returns `None` or `ActionResult.Performed`. The timing that verifications and `generateUnsolicitedEvents` are given, and `prepare`, let `DefaultIntegrationPlugin` fit a person's pace; an automated plugin can ignore them.

To fail a step, throw an exception with a message saying what went wrong. The procedure fails, and the message goes into its log. The same goes for actions the device can't perform; but if the device doesn't support a feature, turn it off with its `supports.*` option instead, so the procedures that need it are skipped.

Every method gets a `PluginReporter` (verifications get a `CheckReporter`, which extends it). Anything you pass to its `log` method appears in the procedure's log, e.g. what the plugin did, or the values it compared.

`startProcedure` and `endProcedure` do nothing by default. Override them to set the device up and tear it down around each procedure that runs; they aren't called for a skipped procedure.

### Automating step by step

`DefaultIntegrationPlugin` is the plugin the CLI uses: every method prompts the user. Extend it, and override only what you've automated:

```scala
class MyDevicePlugin extends DefaultIntegrationPlugin {
  override def cyclePower(reporter: PluginReporter): Unit = {
    fixture.powerOff()
    fixture.powerOn()
    // the next step connects at once, so return only when the device accepts connections again
    fixture.waitUntilListening()
    reporter.log("Cycled power through the test fixture")
  }
}
```

The rest still prompts. You can automate the steps you hit most often first, and run the whole suite unattended once nothing prompts.

## Running in CI

[stepfunc/dnp3](https://github.com/stepfunc/dnp3) runs every procedure on each pull request against its DNP3 outstation, and is a complete working example. Its plugin, in the `conformance` directory, starts the outstation inside the JVM for each procedure, generates events through the outstation's API, and checks controls and attributes against what the outstation recorded. The `conformance` job in its `.github/workflows/ci.yml`:

1. builds the plugin and runs it with `-rev 2.7 -config device.ini -run all`;
2. turns on packet capture, by setting `tcp.capture.adapter` to the loopback adapter, which works because the job runs as root;
3. checks that every procedure which sent frames captured packets;
4. uploads the whole output directory, with the report, logs and captures, as a build artifact, even when the run fails.

A nonzero exit code fails the job, so a procedure that fails fails the build, and the report in the artifact shows why.
