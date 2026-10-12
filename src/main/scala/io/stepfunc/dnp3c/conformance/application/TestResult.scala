// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.io.{ File, FileInputStream }
import java.nio.file.{ Files, Path, StandardCopyOption }
import java.util.Properties

import io.stepfunc.dnp3c.conformance.api.ProcedureOutcome

import scala.util.Using
import scala.util.control.NonFatal

sealed trait TestResult {
  val value: String
}

object TestResult {

  object Pass extends TestResult { val value: String = "pass" }
  object Skip extends TestResult { val value: String = "skip" }
  object Fail extends TestResult { val value: String = "fail" }

  private val key = "result"

  def read(file: File): TestResult = {
    val stream = new FileInputStream(file)
    try {
      val props = new Properties()
      props.load(stream)
      TestResult.from(props.getProperty(key))
    } finally {
      stream.close()
    }
  }

  def from(outcome: ProcedureOutcome): TestResult = outcome match {
    case ProcedureOutcome.Passed => Pass
    case ProcedureOutcome.Failed(_) => Fail
    case ProcedureOutcome.Skipped(_) => Skip
  }

  // writes a temporary file and moves it into place, so the result is never partly written. The temporary file is
  // created like the other report files, not with createTempFile, which makes it readable only by its owner: the
  // results of a run as root (to capture packets) must be readable by everyone the other files are.
  def write(result: TestResult, file: Path): Unit = {
    val temporary = file.resolveSibling(file.getFileName.toString + ".tmp")
    try {
      Using.resource(Files.newOutputStream(temporary)) { stream =>
        val props = new Properties()
        props.setProperty(key, result.value)
        props.store(stream, "test result")
      }
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch {
      case ex: Throwable =>
        try Files.deleteIfExists(temporary)
        catch { case NonFatal(e) => ex.addSuppressed(e) }
        throw ex
    }
  }

  private def from(value: String): TestResult = value match {
    case Pass.value => Pass
    case Skip.value => Skip
    case Fail.value => Fail
    case _ => throw new Exception(s"Unknown TestResult: $value")
  }

}
