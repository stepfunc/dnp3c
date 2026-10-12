// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, IntegerOption, OptionGroup, OptionTypes, TestOption }

object ClassOptions extends OptionGroup {

  val supportClass1: BooleanOption =
    OptionTypes.boolean(this, "supportClass1", true, "Support Class 1 event reporting.")
  val numClass1EventsGenerated: IntegerOption = OptionTypes.integer(
    this,
    "numClass1EventsGenerated",
    3,
    "Number of Class 1 events generated for the 0x07/0x08 count-limited tests (at least 2, so the count limit is exercised).",
    Some(2),
    Some(255)
  )
  val supportClass2: BooleanOption =
    OptionTypes.boolean(this, "supportClass2", true, "Support Class 2 event reporting.")
  val numClass2EventsGenerated: IntegerOption = OptionTypes.integer(
    this,
    "numClass2EventsGenerated",
    3,
    "Number of Class 2 events generated for the 0x07/0x08 count-limited tests (at least 2, so the count limit is exercised).",
    Some(2),
    Some(255)
  )
  val supportClass3: BooleanOption =
    OptionTypes.boolean(this, "supportClass3", true, "Support Class 3 event reporting.")
  val numClass3EventsGenerated: IntegerOption = OptionTypes.integer(
    this,
    "numClass3EventsGenerated",
    3,
    "Number of Class 3 events generated for the 0x07/0x08 count-limited tests (at least 2, so the count limit is exercised).",
    Some(2),
    Some(255)
  )

  override def prefix: String = "class"
  override def description: String = "Class options"
  override def options: List[TestOption] = List(
    supportClass1,
    numClass1EventsGenerated,
    supportClass2,
    numClass2EventsGenerated,
    supportClass3,
    numClass3EventsGenerated
  )

}
