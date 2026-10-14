// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.common.InputType
import io.stepfunc.dnp3c.conformance.common.InputEventProcedures._

object Procedure8_22_2_1 extends NoEvents(InputType.DoubleBitBinaryInput)
object Procedure8_22_2_2 extends AllQualifier(InputType.DoubleBitBinaryInput)
object Procedure8_22_2_3 extends CountQualifier(InputType.DoubleBitBinaryInput, twoByteCount = false)
object Procedure8_22_2_4 extends CountQualifier(InputType.DoubleBitBinaryInput, twoByteCount = true)
object Procedure8_22_2_6 extends AllQualifierWithoutTime(InputType.DoubleBitBinaryInput)
object Procedure8_22_2_7 extends CountQualifierWithoutTime(InputType.DoubleBitBinaryInput, twoByteCount = false)
object Procedure8_22_2_8 extends CountQualifierWithoutTime(InputType.DoubleBitBinaryInput, twoByteCount = true)
object Procedure8_22_2_9 extends AllQualifierWithTime(InputType.DoubleBitBinaryInput)
object Procedure8_22_2_10 extends CountQualifierWithTime(InputType.DoubleBitBinaryInput, twoByteCount = false)
object Procedure8_22_2_11 extends CountQualifierWithTime(InputType.DoubleBitBinaryInput, twoByteCount = true)
object Procedure8_22_2_12 extends AllQualifierWithRelativeTime(InputType.DoubleBitBinaryInput)
object Procedure8_22_2_13 extends CountQualifierWithRelativeTime(InputType.DoubleBitBinaryInput, twoByteCount = false)
object Procedure8_22_2_14 extends CountQualifierWithRelativeTime(InputType.DoubleBitBinaryInput, twoByteCount = true)
object Procedure8_22_2_15 extends RelativeTimeLongInterval(InputType.DoubleBitBinaryInput)
