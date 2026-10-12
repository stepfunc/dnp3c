// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassData.class2
import io.stepfunc.dnp3c.conformance.s8.helpers.UnsolicitedResponseSteps.UnsolicitedClass

object Procedure81123 extends UnsolicitedClass(class2) {

  override def id: String = "8.11.2.3"

  override def description: String = "Unsolicited Response - Class 2 Data"
}
