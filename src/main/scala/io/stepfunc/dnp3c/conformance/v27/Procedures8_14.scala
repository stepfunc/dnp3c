// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.common.InputType
import io.stepfunc.dnp3c.conformance.common.InputEventProcedures._

object Procedure8_14_2_1 extends NoEvents(InputType.BinaryInput)
object Procedure8_14_2_2 extends AllQualifier(InputType.BinaryInput)
object Procedure8_14_2_3 extends CountQualifier(InputType.BinaryInput, twoByteCount = false)
object Procedure8_14_2_4 extends CountQualifier(InputType.BinaryInput, twoByteCount = true)
object Procedure8_14_2_5 extends NoConfirm(InputType.BinaryInput)
object Procedure8_14_2_6 extends AllQualifierWithoutTime(InputType.BinaryInput)
object Procedure8_14_2_7 extends CountQualifierWithoutTime(InputType.BinaryInput, twoByteCount = false)
object Procedure8_14_2_8 extends CountQualifierWithoutTime(InputType.BinaryInput, twoByteCount = true)
object Procedure8_14_2_9 extends AllQualifierWithTime(InputType.BinaryInput)
object Procedure8_14_2_10 extends CountQualifierWithTime(InputType.BinaryInput, twoByteCount = false)
object Procedure8_14_2_11 extends CountQualifierWithTime(InputType.BinaryInput, twoByteCount = true)
object Procedure8_14_2_12 extends AllQualifierWithRelativeTime(InputType.BinaryInput)
object Procedure8_14_2_13 extends CountQualifierWithRelativeTime(InputType.BinaryInput, twoByteCount = false)
object Procedure8_14_2_14 extends CountQualifierWithRelativeTime(InputType.BinaryInput, twoByteCount = true)
object Procedure8_14_2_15 extends RelativeTimeLongInterval(InputType.BinaryInput)
