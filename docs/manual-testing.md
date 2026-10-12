# Testing a device by hand

The dnp3c command-line tool runs the DNP3 IED Level 2 outstation test procedures against a device, the device under test (DUT), and records the results in an HTML report. dnp3c acts as the master and connects to the DUT over TCP.

Most steps are performed by dnp3c itself. Some can't be done over DNP3, such as cycling the DUT's power, generating events on its inputs, or checking that an output operated. For those, dnp3c tells you what to do and waits for you.

You don't have to run every procedure in one sitting. Run any procedure or section, in any order, as many times as you need; each run adds its results to the report.

## Before you start

- Java 17 or newer, and the CLI from the [releases](https://github.com/stepfunc/dnp3c/releases). See the [README](../README.md).
- The DUT, reachable from your machine over TCP.
- The DUT's Device Profile. The configuration describes what the DUT supports, and the profile is where those answers come from.
- If the DUT sends DNP3 keep-alives (link status requests) and closes the connection when one goes unanswered, turn them off for testing. dnp3c answers them only while it's waiting for the DUT, not during its own waits (70.5 s in 8.14.2.15 and 8.22.2.15, longer for some timeouts you configure) or while it waits for you.

## Configure

Write a configuration file with every option and its default:

```
dnp3c -write device.ini
```

Each option has a comment explaining it. The ones to set first:

| Option | What it is |
|---|---|
| `test.deviceUnderTest` | The DUT's name in the report, e.g. its vendor, model and firmware version |
| `test.output.directory` | Where results and the report are written |
| `tcp.device.address`, `tcp.device.port` | Where the DUT listens |
| `link.source`, `link.destination` | The link addresses of dnp3c (the master) and of the DUT |
| `supports.*` | Which optional features the DUT supports |

Then work through the rest: the point indices for controls and analog outputs, event classes and buffer sizes, time synchronization, unsolicited responses, and device attributes.

The file is a Java properties file in UTF-8, where a backslash starts an escape: write Windows paths with forward slashes (`C:/Conformance/Relay1`) or doubled backslashes (`C:\\Conformance\\Relay1`).

Pass the file with `-config` on every command. Without `-config`, dnp3c uses `./device.properties`, and creates it with the defaults if it doesn't exist.

dnp3c checks the whole file before doing anything else, and lists every option it doesn't recognize or whose value is invalid. Options you leave out of the file have their defaults.

`dnp3c -options` prints every option and its default without writing a file.

## See what will run

The procedures are organized in sections: 6 (link layer), 7 (transport layer) and 8 (application layer), each with subsections. `dnp3c -help` lists the top-level sections.

```
dnp3c -config device.ini -describe 8.6       # the procedures in a section
dnp3c -config device.ini -describe 8.6.5.4   # the steps of one procedure
dnp3c -config device.ini -disabled           # optional procedures your configuration turns off
```

Procedures for features the DUT doesn't support are optional. If a `supports.*` option turns one off, it's recorded as skipped instead of run.

## Run

`-run` takes the id of any procedure or section, or `all`:

```
dnp3c -config device.ini -run 8.6.5.4   # one procedure
dnp3c -config device.ini -run 8.6       # a section
dnp3c -config device.ini -run all       # everything
```

When a step needs you, dnp3c prints what to do:

- **Actions**, such as "Cycle power to the DUT": do it, then press ENTER. After cycling power, wait until the DUT has restarted and accepts connections: the next step connects straight away. A timed action says "Press ENTER first, then within 10.0 s: …": press ENTER once, then do it, and don't press ENTER again; an extra ENTER would answer the next prompt.
- **Verifications**, such as checking that a binary output operated: enter `p` if it did what's described, or `f` if not. Entering `f` fails the procedure.

Some questions come after a procedure's steps, where stopping for your answer would change what the procedure tests. Each says which step it's from. Prompts that ask you to note the events you generate, or to watch an output, are for those later questions.

If you miss a time limit, the procedure fails with a message saying so; rerun it. A limit that's too short to work to by hand is usually a timeout on the DUT that you can lengthen, along with its option here.

A run stops at the first procedure that fails, and the procedures after it don't run. The exit code is 0 if every procedure passed or was skipped, and nonzero otherwise.

## Results

Each procedure writes to its own folder in the output directory:

```
results/
  report.html          the report, linking to each procedure's log and capture
  report.css
  8.6.5.4/
    log.html           every step, frame and check, with the outcome
    results.properties the result: pass, fail or skip
    dump.pcap          the packet capture, if capture is on
```

The report is rebuilt after every run, including runs that fail. It shows every procedure: the ones you've run with their result, and the rest as not run. To rebuild it without running anything, use `dnp3c -config device.ini -report`.

## Working incrementally

Because each procedure has its own folder, results accumulate across runs. You can test a section a day, or fix the DUT after a failure and rerun just the procedure that failed. Everything else in the report stays as it was.

Things to know:

- **A rerun replaces the procedure's previous log, result and capture.** Earlier attempts aren't kept.
- **The report doesn't record which configuration or firmware produced each result.** If you change either, start a new output directory, so one report never mixes results from different setups.
- **There's no "from here to the end".** After a failure in the middle of a section, rerun the section, or run the remaining procedures one at a time.
- **A procedure that fails partway may leave the DUT's configuration changed.** Before running others, put the DUT back as it was: for example its link confirms, unsolicited settings and master address, and any points it uninstalled.

## Packet captures

dnp3c can capture the traffic of each procedure into its `dump.pcap`, which opens in Wireshark with DNP3 decoding. Capturing needs libpcap (Npcap on Windows) and the privileges to capture, typically root on Linux. Packet capture doesn't work on macOS.

List the network adapters and their addresses:

```
dnp3c -adapters
```

Set `tcp.capture.adapter` to the address of the adapter that reaches the DUT, exactly as `-adapters` prints it, including the leading `/`, e.g. `/192.168.1.10`. Only traffic on the DUT's port is captured. Leave the option empty to turn capture off.
