// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

/** The names of the files a run writes: each procedure's in its own folder, and the report beside those folders */
object ResultFiles {

  // a procedure's result, log and capture
  val result: String = "results.properties"
  val log: String = "log.html"
  val capture: String = "dump.pcap"

  // the report of every procedure's result, and its stylesheet
  val report: String = "report.html"
  val stylesheet: String = "report.css"
}
