// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ Index, IndexOption, OptionGroup, OptionTypes, TestOption }

object FlagTestOptions extends OptionGroup {

  val individualRemoteSupervisoryOutputIndex: IndexOption = OptionTypes.index(
    this,
    "individualRemoteSupervisoryOutputIndex",
    Index(0),
    "binary output point used in test case 8.6.4.3")

  override def prefix: String = "flag.tests"

  override def description: String = "Options related to tests in section 8.6"

  override def options: List[TestOption] = List(individualRemoteSupervisoryOutputIndex)

}
