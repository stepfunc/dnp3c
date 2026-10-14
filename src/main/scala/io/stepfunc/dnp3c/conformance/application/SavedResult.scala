// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.io.{ File, FileInputStream }
import java.nio.file.{ Files, Path, StandardCopyOption }
import java.util.Properties

import io.stepfunc.dnp3c.conformance.api.ProcedureOutcome

import scala.util.Using
import scala.util.control.NonFatal

/**
 * A procedure's result, as saved in its folder
 *
 * @param revision the revision it was run under; None for a result saved before results recorded one (0.1.0)
 * @param level the subset level it was run at; None for a result saved before results recorded one
 * @param notes what the procedure noted about a pass, e.g. that it's to IEEE 1815-2012 but not 1815-202x
 */
case class SavedResult(
    outcome: ProcedureOutcome,
    revision: Option[String],
    level: Option[Int],
    notes: List[String] = Nil)

object SavedResult {

  // the result values
  private val pass = "pass"
  private val skip = "skip"
  private val fail = "fail"

  private val resultKey = "result"
  private val revisionKey = "revision"
  private val levelKey = "subsetLevel"
  private val reasonKey = "reason"
  private val noteKey = "note"

  def read(file: File): SavedResult = {
    val stream = new FileInputStream(file)
    try {
      val props = new Properties()
      props.load(stream)
      val reason = Option(props.getProperty(reasonKey)).getOrElse("")
      val outcome = props.getProperty(resultKey) match {
        case `pass` => ProcedureOutcome.Passed
        case `fail` => ProcedureOutcome.Failed(reason)
        case `skip` => ProcedureOutcome.Skipped(reason)
        case value => throw new Exception(s"Unknown result: $value")
      }
      SavedResult(
        outcome,
        Option(props.getProperty(revisionKey)),
        Option(props.getProperty(levelKey)).flatMap(_.toIntOption),
        Iterator.from(1).map(i => props.getProperty(s"$noteKey.$i")).takeWhile(_ != null).toList
      )
    } finally {
      stream.close()
    }
  }

  // writes a temporary file and moves it into place, so the result is never partly written. The temporary file is
  // created like the other report files, not with createTempFile, which makes it readable only by its owner: the
  // results of a run as root (to capture packets) must be readable by everyone the other files are.
  def write(saved: SavedResult, file: Path): Unit = {
    val temporary = file.resolveSibling(file.getFileName.toString + ".tmp")
    try {
      Using.resource(Files.newOutputStream(temporary)) { stream =>
        val props = new Properties()
        saved.outcome match {
          case ProcedureOutcome.Passed => props.setProperty(resultKey, pass)
          case ProcedureOutcome.Failed(message) =>
            props.setProperty(resultKey, fail)
            props.setProperty(reasonKey, message)
          case ProcedureOutcome.Skipped(reason) =>
            props.setProperty(resultKey, skip)
            props.setProperty(reasonKey, reason)
        }
        saved.revision.foreach(props.setProperty(revisionKey, _))
        saved.level.foreach(l => props.setProperty(levelKey, l.toString))
        saved.notes.zipWithIndex.foreach((n, i) => props.setProperty(s"$noteKey.${i + 1}", n))
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

}
