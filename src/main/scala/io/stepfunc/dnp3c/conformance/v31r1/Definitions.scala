// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.common.Validations.{ isNullErrorResponse, isNullOrNullErrorResponse }
import io.stepfunc.dnp3c.conformance.v27.BinaryOutputStatusRead
import io.stepfunc.dnp3c.conformance.v27.AnalogOutputStatusRead
import io.stepfunc.dnp3c.dnp3.app.IIN
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group40Var1, Group40Var2 }

/** The definitions of 3.1r1 tests that differ from 2.7's: 2.7's, with 3.1r1's rules where only a rule changed */
object Definitions {

  /** 8.1.2: a device without binary outputs answers with a Null Error Response, IIN2.1 or IIN2.2 */
  val binaryOutputStatus = new BinaryOutputStatusRead(isNullErrorResponse(IIN.objectErrors))

  /**
   * 8.3.2: analog output status in g40v2, or in g40v1 too at level 3; a device without analog outputs answers with a
   * Null Response, or a Null Error Response with IIN2.1 or IIN2.2. (8.3.1 says it must give the error response; the
   * test's step 5 accepts either, which is what runs.)
   */
  val analogOutputStatus = new AnalogOutputStatusRead(
    options =>
      if (options.get(OutstationOptions.subsetLevel).contains(3)) Set(Group40Var1, Group40Var2) else Set(Group40Var2),
    isNullOrNullErrorResponse(IIN.objectErrors)
  )
}
