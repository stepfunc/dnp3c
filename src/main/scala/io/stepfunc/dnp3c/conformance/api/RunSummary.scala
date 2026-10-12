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
}

case class ProcedureResult(id: String, outcome: ProcedureOutcome)

/**
 * The procedures a run reached, in order, and the ones it didn't because it stopped at a failure
 */
case class RunSummary(results: List[ProcedureResult], unreached: List[String]) {

  def succeeded: Boolean = unreached.isEmpty && !results.exists(_.outcome.isInstanceOf[ProcedureOutcome.Failed])
}

/**
 * Thrown when the harness or the plugin fails while running a procedure, as opposed to the procedure failing
 *
 * @param procedureId the procedure that was running
 * @param partial the results so far, including the running procedure's outcome if it had one
 */
class HarnessExecutionException(val procedureId: String, val partial: RunSummary, cause: Throwable)
    extends Exception(s"Procedure $procedureId couldn't be run: ${ErrorText.of(cause)}", cause)
