// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

/**
 * How a procedure ended
 */
sealed trait ProcedureOutcome

object ProcedureOutcome {
  case object Passed extends ProcedureOutcome
  case class Failed(message: String) extends ProcedureOutcome
  case class Skipped(reason: String) extends ProcedureOutcome

  /**
   * The configuration left it out (`test.exclude.<revision id>/<id>`), for this reason. Unlike a skipped procedure,
   * it applies to the device, so the device hasn't been shown to conform to it.
   */
  case class Excluded(reason: String) extends ProcedureOutcome
}

/**
 * A procedure's outcome
 *
 * @param notes what the procedure noted about a pass, e.g. that the device complies with IEEE 1815-2012 but not
 *              1815-202x; empty otherwise
 */
case class ProcedureResult(id: String, outcome: ProcedureOutcome, notes: List[String] = Nil)

/**
 * The procedures a run reached, in order, and the ones it didn't because it stopped at a failure
 */
case class RunSummary(results: List[ProcedureResult], unreached: List[String]) {

  /**
   * Whether the run completed without a failure: it reached every procedure, and each passed, was skipped or was
   * excluded. It says the run succeeded, not that the device conforms: see [[exclusions]].
   */
  def succeeded: Boolean = unreached.isEmpty && !results.exists(_.outcome.isInstanceOf[ProcedureOutcome.Failed])

  /** the procedures the configuration excluded, in order, which the device hasn't been shown to conform to */
  def exclusions: List[ProcedureResult] = results.filter(_.outcome.isInstanceOf[ProcedureOutcome.Excluded])
}

/**
 * Thrown when the harness or the plugin fails while running a procedure, as opposed to the procedure failing
 *
 * @param procedureId the procedure that was running
 * @param partial the results so far, including the running procedure's outcome if it had one
 */
class HarnessExecutionException(val procedureId: String, val partial: RunSummary, cause: Throwable)
    extends Exception(s"Procedure $procedureId couldn't be run: ${ErrorText.of(cause)}", cause)
