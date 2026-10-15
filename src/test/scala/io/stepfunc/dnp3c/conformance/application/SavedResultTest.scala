// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.nio.file.{ FileSystems, Files }

import io.stepfunc.dnp3c.conformance.api.ProcedureOutcome

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class SavedResultTest extends AnyFunSuite with Matchers {

  test("a result file has the permissions of any other file the run writes") {
    assume(FileSystems.getDefault.supportedFileAttributeViews.contains("posix"))
    val directory = Files.createTempDirectory("dnp3c")
    val other = Files.write(directory.resolve("log.html"), Array.emptyByteArray)
    val results = directory.resolve("results.properties")
    SavedResult.write(SavedResult(ProcedureOutcome.Passed, Some("2.7"), Some(2)), results)
    Files.getPosixFilePermissions(results) should equal(Files.getPosixFilePermissions(other))
    List(results, other, directory).foreach(Files.delete)
  }

  test("a result keeps its revision, level and reason, and one saved by 0.1.0 has none of them") {
    val directory = Files.createTempDirectory("dnp3c")
    val results = directory.resolve("results.properties")
    val saved = SavedResult(ProcedureOutcome.Skipped("disabled because x != true"), Some("2.7"), Some(2))
    SavedResult.write(saved, results)
    SavedResult.read(results.toFile) should equal(saved)
    Files.writeString(results, "result=pass\n")
    SavedResult.read(results.toFile) should equal(SavedResult(ProcedureOutcome.Passed, None, None))
    List(results, directory).foreach(Files.delete)
  }

  test("a pass keeps its warnings and notes, each in order, as warning.N and note.N") {
    val directory = Files.createTempDirectory("dnp3c")
    val results = directory.resolve("results.properties")
    val saved = SavedResult(
      ProcedureOutcome.Passed,
      Some("3.1r1"),
      Some(2),
      notes = List("a note"),
      warnings = List("complies with 1815-2012 only", "point 0 only in 32 bits"))
    SavedResult.write(saved, results)
    val written = Files.readString(results)
    written should include("warning.1=complies with 1815-2012 only")
    written should include("warning.2=point 0 only in 32 bits")
    written should include("note.1=a note")
    SavedResult.read(results.toFile) should equal(saved)
    List(results, directory).foreach(Files.delete)
  }

  test("an excluded result keeps its reason, and can't be read without one") {
    val directory = Files.createTempDirectory("dnp3c")
    val results = directory.resolve("results.properties")
    val saved = SavedResult(ProcedureOutcome.Excluded("Assign Class isn't supported: #480"), Some("3.1r1"), Some(2))
    SavedResult.write(saved, results)
    Files.readString(results) should include("result=excluded")
    SavedResult.read(results.toFile) should equal(saved)
    for (reason <- List("", "reason=\n", "reason=  \n")) {
      Files.writeString(results, "result=excluded\nrevision=3.1r1\nsubsetLevel=2\n" + reason)
      an[Exception] should be thrownBy SavedResult.read(results.toFile)
    }
    List(results, directory).foreach(Files.delete)
  }
}
