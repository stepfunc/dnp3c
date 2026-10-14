// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.common.EventClassData.{ class1, class2, class3 }
import io.stepfunc.dnp3c.conformance.common.UnsolicitedResponseSteps.UnsolicitedClass

object Procedure8_11_2_2 extends UnsolicitedClass(class1)
object Procedure8_11_2_3 extends UnsolicitedClass(class2)
object Procedure8_11_2_4 extends UnsolicitedClass(class3)
