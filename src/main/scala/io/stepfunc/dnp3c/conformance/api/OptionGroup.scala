// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

trait OptionGroup {

  def prefix: String
  def description: String
  def options: List[TestOption]

}
