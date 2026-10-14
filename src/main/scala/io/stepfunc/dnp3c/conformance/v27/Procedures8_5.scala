// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.common.ClassEventProcedures.{ AllQualifier, CountQualifier, NoConfirm }
import io.stepfunc.dnp3c.conformance.common.EventClassData.{ class1, class2, class3 }

object Procedure8_5_2_2_1 extends AllQualifier(class1)
object Procedure8_5_2_2_2 extends CountQualifier(class1, twoByteCount = false)
object Procedure8_5_2_2_3 extends CountQualifier(class1, twoByteCount = true)
object Procedure8_5_2_2_4 extends NoConfirm(class1)
object Procedure8_5_3_2_1 extends AllQualifier(class2)
object Procedure8_5_3_2_2 extends CountQualifier(class2, twoByteCount = false)
object Procedure8_5_3_2_3 extends CountQualifier(class2, twoByteCount = true)
object Procedure8_5_3_2_4 extends NoConfirm(class2)
object Procedure8_5_4_2_1 extends AllQualifier(class3)
object Procedure8_5_4_2_2 extends CountQualifier(class3, twoByteCount = false)
object Procedure8_5_4_2_3 extends CountQualifier(class3, twoByteCount = true)
object Procedure8_5_4_2_4 extends NoConfirm(class3)
