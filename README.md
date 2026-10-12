# dnp3c

A DNP3 conformance test harness from [Step Function I/O](https://stepfunc.io). It runs the DNP3 IED Level 2 outstation certification test procedures against a device and produces an HTML report of the results.

dnp3c can be used in two ways:

- **As a command-line tool**, for testing a device by hand. Steps the harness can't perform over DNP3, such as cycling power or generating events, are prompted for.
- **As a library**, for automated testing, e.g. in CI. You implement `IntegrationPlugin` to perform those steps on your device and run the procedures with `io.stepfunc.dnp3c.conformance.Harness`.

## Requirements

Java 17 or newer.

## Command-line tool

Download `dnp3c-<version>.zip` from the [releases](https://github.com/stepfunc/dnp3c/releases), unzip it, and run `bin/dnp3c` (`bin\dnp3c.cmd` on Windows). The launcher uses the Java in `JAVA_HOME`, or on the `PATH`, and tells you if it's missing or too old.

```
dnp3c -write device.ini                  # write a configuration file with every option
dnp3c -config device.ini -run all        # run every procedure and write the report
dnp3c -config device.ini -describe 8.23  # print the steps of a section or procedure
dnp3c -help                              # list the commands and test sections
```

Results, including `report.html`, are written to the directory set by `test.output.directory`. See [Testing a device by hand](docs/manual-testing.md) for the whole workflow.

```
$ dnp3c -help
       __           _____
  ____/ /___  ____ |__  /_____
 / __  / __ \/ __ \ /_ </ ___/
/ /_/ / / / / /_/ /__/ / /__
\__,_/_/ /_/ .___/____/\___/
          /_/

DNP3 conformance testing engine

Copyright Step Function I/O LLC
https://stepfunc.io

 usage:  dnp3c [-adapters] [-config <arg>] [-describe <arg>] [-disabled]
    [-help] [-options] [-report] [-run <arg>] [-write <arg>]

     Options                              Description
 -adapters           List all of the available network adapters and their
                      addresses
 -config <arg>       Path to configuration file
 -describe <arg>     Describe the steps to be executed in the specified
                      procedure
 -disabled           List all optional test procedures that are disabled by
                      configuration
 -help               Print this help information
 -options            List all of the available configuration file options
 -report             Generate an HTML report containing all the results
 -run <arg>          Run the specified test section/procedure
 -write <arg>        Write a default configuration file to the specified
                      path

sections:

6                             Link Layer
7                             Transport Layer
8                             Application Layer
```

## Library

The library jar, `io.stepfunc:dnp3c`, is published to Maven Central. See [Automating the tests](docs/automation.md) for writing an integration plugin and running it in CI.

## Known limitations

- **Not implemented:** 8.10.2 (multi-drop support) and 8.12.2 (collision avoidance). The report shows them struck through.
- **Report by exception** isn't verified: where a procedure requires a device whose class 0 response spans several link frames to also report by exception, the log notes it but the tool doesn't check it.
- **8.11.2.5.8** covers a device that holds unsolicited responses until it has a minimum number of events, not one that holds them for a time.
- **8.11.2.1 step 24:** the procedure expects a READ to be answered while the device's startup unsolicited response is still unconfirmed, while IEEE 1815's outstation state table would let the device defer it. The tool follows the procedure.
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
