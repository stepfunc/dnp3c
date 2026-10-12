// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

/**
 * A check that runs after the procedure's steps instead of during them, e.g. a question for a manual tester that
 * would otherwise hold up a timed step. It throws to fail the procedure.
 *
 * @param description what is checked
 * @param evidence what the check is judged from, captured when the check was created
 */
case class DeferredCheck(description: String, evidence: List[String], check: () => Unit)
