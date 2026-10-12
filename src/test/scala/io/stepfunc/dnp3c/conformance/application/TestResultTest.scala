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
    TestResult.write(TestResult.Pass, results)
    Files.getPosixFilePermissions(results) should equal(Files.getPosixFilePermissions(other))
    List(results, other, directory).foreach(Files.delete)
  }
}
