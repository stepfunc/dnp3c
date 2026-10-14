# dnp3c

A DNP3 conformance test harness from [Step Function I/O](https://stepfunc.io). It runs the outstation test procedures of the DNP3 IED Certification Procedure, Subset Level 2, version 2.7, against a device and produces an HTML report of the results.

dnp3c can be used in two ways:

- **As a command-line tool**, for testing a device by hand. Steps the harness can't perform over DNP3, such as cycling power or generating events, are prompted for.
- **As a library**, for automated testing, e.g. in CI. You implement `IntegrationPlugin` to perform those steps on your device and run the procedures with `io.stepfunc.dnp3c.conformance.Harness`.

![dnp3c running section 8.5 against an outstation](docs/images/run.gif)

*Section 8.5 (Class Data) run against the outstation of the [dnp3](https://github.com/stepfunc/dnp3) library, by the conformance tests its CI runs. Their `IntegrationPlugin` generates the events that a tester would otherwise generate by hand.*

## The report

Commercial DNP3 test tools save their results in proprietary formats. dnp3c writes plain HTML and pcap files:

- **The report is a web page.** It shows every procedure with its result, and it opens in any browser. You can archive it, email it, or publish it next to a release, and nobody needs a license to read it.
- **Every result can be traced.** Each procedure links to a log of every step, every frame it sent and received, decoded at each layer, and every check with its outcome.
- **The traffic is in Wireshark's format.** With capture on, each procedure's traffic is saved as a pcap that opens in Wireshark, decoded as DNP3.

![The report's summary](docs/images/report.png)

![A procedure's log](docs/images/log.png)

## Test procedures

dnp3c implements the DNP Users Group's *DNP3 IED Certification Procedure, Subset Level 2*, version 2.7 (2 March 2016). The section, procedure and step numbers in the tool, its logs and the report are those of 2.7. Where a technical bulletin replaces a 2.7 test outright, dnp3c runs the replacement: TB2017-001's 8.6.4.2 and 8.6.4.3, whose steps are numbered 1.a, 1.b, and so on.

Later editions, 3.0 (December 2020) and 3.1 rev 1 (September 2022), cover Subset Levels 1 to 3. They renumber some tests, leave many tests without a number, and change what some tests send or accept, often to apply technical bulletins published after 2.7. A result from dnp3c can therefore differ from what those editions would give. Support for 3.1 rev 1 alongside 2.7 is in progress: `-rev 3.1r1` has its whole tree of tests, by its own numbers. The ones whose steps match a 2.7 test already run; the rest show as not implemented, and are skipped.

## Requirements

Java 17 or newer.

## Command-line tool

Download `dnp3c-<version>.zip` from the [releases](https://github.com/stepfunc/dnp3c/releases), unzip it, and run `bin/dnp3c` (`bin\dnp3c.cmd` on Windows). The launcher uses the Java in `JAVA_HOME`, or on the `PATH`, and tells you if it's missing or too old.

```
dnp3c -write device.ini                          # write a configuration file with every option
dnp3c -revs                                      # list the revisions of the test procedures
dnp3c -rev 2.7 -config device.ini -run all       # run every procedure and write the report
dnp3c -rev 2.7 -config device.ini -describe 8.23 # print the steps of a section or procedure
dnp3c -rev 2.7 -help                             # list the commands and test sections
```

Results, including `report.html`, are written to the revision's folder of the directory set by `test.output.directory`, e.g. `results/2.7/L2/`. The configuration must set `outstation.subset.level`, the DNP3 subset level the device is configured for: it has no default. See [Testing a device by hand](docs/manual-testing.md) for the whole workflow.

## Library

The library jar, `io.stepfunc:dnp3c`, is published to Maven Central. See [Automating the tests](docs/automation.md) for writing an integration plugin and running it in CI.

## Known limitations

- **Not implemented:** 8.10.2 (multi-drop support) and 8.12.2 (collision avoidance). The report shows them struck through.
- **Report by exception** isn't verified: where a procedure requires a device whose class 0 response spans several link frames to also report by exception, the log notes it but the tool doesn't check it.
- **8.11.2.5.8** covers a device that holds unsolicited responses until it has a minimum number of events, not one that holds them for a time.
- **8.11.2.1 step 24:** the procedure expects a READ to be answered while the device's startup unsolicited response is still unconfirmed, while IEEE 1815's outstation state table would let the device defer it. The tool follows the procedure.
- **8.6.4:** 2.7's description of the desired behavior still has a binary output in local mode set LOCAL_FORCED. TB2017-001 replaced the tests, which the tool runs, but not that description: in local mode an output clears ONLINE and keeps its other flags.
- **Counter events in 3.1r1** may be changes of a counter's flags alone (TB2017-003a). The default plugin's prompts for 8.17 ask for counts, so a device that reports only flag changes needs a plugin that generates and checks those instead.
- **3.1r1's 6.6.2-4** (No Inter-frame Gap) is for serial and UDP, and for TCP only where the device has more than one DNP3 address. dnp3c connects over TCP only, so it runs the test only for such a device (`supports.multipleAddresses`).
- **Packet capture** doesn't work on macOS.

## Building

Building needs a full JDK 21, which can compile for Java 17.

```
mvn test       # compile and run the unit tests
mvn package    # build the library jar, the runnable jar and the CLI zip in target/
```

## License

dnp3c is licensed under the [GNU General Public License v3.0 only](LICENSE).

Copyright Step Function I/O LLC
