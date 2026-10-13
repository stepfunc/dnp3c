// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.nio.file.{ FileSystems, Files }

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class TestResultTest extends AnyFunSuite with Matchers {

  test("a result file has the permissions of any other file the run writes") {
    assume(FileSystems.getDefault.supportedFileAttributeViews.contains("posix"))
    val directory = Files.createTempDirectory("dnp3c")
    val other = Files.write(directory.resolve("log.html"), Array.emptyByteArray)
    val results = directory.resolve("results.properties")
    TestResult.write(TestResult.Saved(TestResult.Pass, Some("2.7"), Some(2), None), results)
    Files.getPosixFilePermissions(results) should equal(Files.getPosixFilePermissions(other))
    List(results, other, directory).foreach(Files.delete)
  }

  test("a result keeps its revision, level and reason, and one saved by 0.1.0 has none of them") {
    val directory = Files.createTempDirectory("dnp3c")
    val results = directory.resolve("results.properties")
    val saved = TestResult.Saved(TestResult.Skip, Some("2.7"), Some(2), Some("disabled because x != true"))
    TestResult.write(saved, results)
    TestResult.read(results.toFile) should equal(saved)
    Files.writeString(results, "result=pass\n")
    TestResult.read(results.toFile) should equal(TestResult.Saved(TestResult.Pass, None, None, None))
    List(results, directory).foreach(Files.delete)
  }
}
