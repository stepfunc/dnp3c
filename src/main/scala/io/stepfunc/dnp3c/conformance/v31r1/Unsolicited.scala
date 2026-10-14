// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.api.options.UnsolicitedOptions
import io.stepfunc.dnp3c.conformance.common.UnsolicitedResponseSteps.RetryLimit
import io.stepfunc.dnp3c.conformance.v27.UnsolicitedAndPolledResponses

/** 3.1r1's 8.11 tests whose steps differ from 2.7's */
object Unsolicited {

  /**
   * 8.11.2.5, where 8.11.2.5.9 step 1 configures the limited number of retries closest to 5 that the DUT supports. A
   * DUT that can't be configured to one, or only to one more than 10, has that noted, and the test goes on at step 19;
   * step 28 then expects more retries than a limit of more than 10, as it does than one it tested.
   */
  val unsolicitedAndPolledResponses: ProcedureDefinition = new UnsolicitedAndPolledResponses(options =>
    if (!options.get(UnsolicitedOptions.retryLimitConfigurable))
      RetryLimit.NotTested(
        "the DUT's maximum number of unsolicited retries can't be configured, so the test of a limited number of " +
          "retries wasn't performed")
    else {
      val retries = options.get(UnsolicitedOptions.retryLimit)
      if (retries > 10)
        RetryLimit.NotTested(
          s"the DUT's maximum number of unsolicited retries closest to 5 is $retries, more than 10, so the test of a " +
            "limited number of retries wasn't performed",
          Some(retries)
        )
      else RetryLimit.Tested(retries)
    })
}
