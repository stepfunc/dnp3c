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

  /**
   * A result as saved in a procedure's folder
   *
   * @param revision the revision it was run under; None for a result saved before results recorded one (0.1.0)
   * @param level the subset level it was run at; None for a result saved before results recorded one
   * @param reason why the procedure was skipped or failed
   */
  case class Saved(result: TestResult, revision: Option[String], level: Option[Int], reason: Option[String])

  private val resultKey = "result"
  private val revisionKey = "revision"
  private val levelKey = "subsetLevel"
  private val reasonKey = "reason"

  def read(file: File): Saved = {
    val stream = new FileInputStream(file)
    try {
      val props = new Properties()
      props.load(stream)
      Saved(
        TestResult.from(props.getProperty(resultKey)),
        Option(props.getProperty(revisionKey)),
        Option(props.getProperty(levelKey)).flatMap(_.toIntOption),
        Option(props.getProperty(reasonKey))
      )
    } finally {
      stream.close()
    }
  }

  def from(outcome: ProcedureOutcome): TestResult = outcome match {
    case ProcedureOutcome.Passed => Pass
    case ProcedureOutcome.Failed(_) => Fail
    case ProcedureOutcome.Skipped(_) => Skip
  }

  def reason(outcome: ProcedureOutcome): Option[String] = outcome match {
    case ProcedureOutcome.Passed => None
    case ProcedureOutcome.Failed(message) => Some(message)
    case ProcedureOutcome.Skipped(reason) => Some(reason)
  }

  // writes a temporary file and moves it into place, so the result is never partly written. The temporary file is
  // created like the other report files, not with createTempFile, which makes it readable only by its owner: the
  // results of a run as root (to capture packets) must be readable by everyone the other files are.
  def write(saved: Saved, file: Path): Unit = {
    val temporary = file.resolveSibling(file.getFileName.toString + ".tmp")
    try {
      Using.resource(Files.newOutputStream(temporary)) { stream =>
        val props = new Properties()
        props.setProperty(resultKey, saved.result.value)
        saved.revision.foreach(props.setProperty(revisionKey, _))
        saved.level.foreach(l => props.setProperty(levelKey, l.toString))
        saved.reason.foreach(props.setProperty(reasonKey, _))
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
