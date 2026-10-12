// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import scala.collection.{ immutable, mutable }

class OptionTracker(source: OptionSource) extends OptionSource {

  private val options: mutable.Set[TestOption] = mutable.Set[TestOption]()

  def getUsedOptions: immutable.Set[TestOption] = {
    options.toSet
  }

  def get(option: TypedTestOption[?]): option.Value = {
    options.add(option)
    source.get(option)
  }

}
