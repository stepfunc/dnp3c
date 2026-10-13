// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s822

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.CountQualifierWithoutTime
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure82227 extends CountQualifierWithoutTime(InputType.DoubleBitBinaryInput, twoByteCount = false)
