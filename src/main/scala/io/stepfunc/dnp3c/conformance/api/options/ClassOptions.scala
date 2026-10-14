// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  BooleanOption, IndexListOption, IntegerOption, OptionGroup, OptionTypes, TestOption
}

object ClassOptions extends OptionGroup {

  // the options of event class n, 1 to 3
  final class EventClass private[ClassOptions] (n: Int) {
    val supported: BooleanOption =
      OptionTypes.boolean(ClassOptions, s"supportClass$n", true, s"Support Class $n event reporting.")
    val numEventsGenerated: IntegerOption = OptionTypes.integer(
      ClassOptions,
      s"numClass${n}EventsGenerated",
      3,
      s"Number of Class $n events generated for the 0x07/0x08 count-limited tests (at least 2, so the count limit is " +
        "exercised).",
      Some(2),
      Some(255)
    )
    val binaryInputs: IndexListOption = OptionTypes.indexList(
      ClassOptions,
      s"assign.class${n}BinaryInputs",
      s"Binary inputs in class $n at startup, whose class 3.1r1's 8.26 assigns. Empty for none",
      Nil
    )
  }

  val class1: EventClass = new EventClass(1)
  val class2: EventClass = new EventClass(2)
  val class3: EventClass = new EventClass(3)

  override def prefix: String = "class"
  override def description: String = "Class options"
  override def options: List[TestOption] = List(class1, class2, class3).map(_.binaryInputs) :::
    List(class1, class2, class3).flatMap(c => List(c.supported, c.numEventsGenerated))

}
