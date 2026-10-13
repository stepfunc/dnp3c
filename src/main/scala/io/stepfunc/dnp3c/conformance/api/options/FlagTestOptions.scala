// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ Index, IndexOption, OptionGroup, OptionTypes, TestOption }

object FlagTestOptions extends OptionGroup {

  val individualRemoteSupervisoryOutputIndex: IndexOption = OptionTypes.index(
    this,
    "individualRemoteSupervisoryOutputIndex",
    Index(0),
    "Binary output that 8.6.4.3 puts in local mode")

  val individualRemoteSupervisoryRemoteOutputIndex: IndexOption = OptionTypes.index(
    this,
    "individualRemoteSupervisoryRemoteOutputIndex",
    Index(1),
    "Binary output that 8.6.4.3 leaves in remote mode while the other is local; it must be a different output"
  )

  override def prefix: String = "flag.tests"

  override def description: String = "Options related to tests in section 8.6"

  override def options: List[TestOption] =
    List(individualRemoteSupervisoryOutputIndex, individualRemoteSupervisoryRemoteOutputIndex)

}
