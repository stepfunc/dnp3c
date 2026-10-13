// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s842

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.dnp3.app.AppFunction
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.ToUninstalledPoint

object Procedure84222 extends ToUninstalledPoint(OperationType.AnalogOutput, AppFunction.directOp)
