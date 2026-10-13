// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s822

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.dnp3.app.AppFunction
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.ToUninstalledPoint

object Procedure82222 extends ToUninstalledPoint(OperationType.BinaryOutput, AppFunction.directOp)
