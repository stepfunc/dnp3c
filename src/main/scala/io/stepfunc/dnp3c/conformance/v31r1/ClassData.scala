// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.common.ClassEventProcedures.{ AllQualifier, CountQualifier }
import io.stepfunc.dnp3c.conformance.common.EventClassData.{ class1, class2, class3 }
import io.stepfunc.dnp3c.conformance.common.EventClassSteps.CountConfirmSteps
import io.stepfunc.dnp3c.conformance.v27.ClassAssignmentVerification

/**
 * 3.1r1's 8.5 tests whose steps differ from 2.7's, beyond the subset level Tables 8-3 and 8-4 apply at, which the
 * shared definitions take from the options. The 0x06 tests also check that the class's IIN1 bit is clear once its
 * events are all reported (step 9), and the others number the confirmations the master sends as steps of their own.
 */
object ClassData {

  val class1All: ProcedureDefinition = new AllQualifier(class1, classBitCleared = true)
  val class2All: ProcedureDefinition = new AllQualifier(class2, classBitCleared = true)
  val class3All: ProcedureDefinition = new AllQualifier(class3, classBitCleared = true)

  // 8.5.2.2.2 numbers only the second confirmation
  val class1OneByteCount: ProcedureDefinition =
    new CountQualifier(class1, twoByteCount = false, CountConfirmSteps(first = false, second = true))
  val class1TwoByteCount: ProcedureDefinition = new CountQualifier(class1, twoByteCount = true, CountConfirmSteps.both)
  val class2OneByteCount: ProcedureDefinition = new CountQualifier(class2, twoByteCount = false, CountConfirmSteps.both)
  val class2TwoByteCount: ProcedureDefinition = new CountQualifier(class2, twoByteCount = true, CountConfirmSteps.both)
  val class3OneByteCount: ProcedureDefinition = new CountQualifier(class3, twoByteCount = false, CountConfirmSteps.both)
  val class3TwoByteCount: ProcedureDefinition = new CountQualifier(class3, twoByteCount = true, CountConfirmSteps.both)

  val classAssignment: ProcedureDefinition = new ClassAssignmentVerification(confirmSteps = true)
}
