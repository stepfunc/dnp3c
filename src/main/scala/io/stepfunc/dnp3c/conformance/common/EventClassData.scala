// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, IntegerOption }
import io.stepfunc.dnp3c.conformance.api.options.ClassOptions
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.{ EventClass, Message, Sequence }
import io.stepfunc.dnp3c.dnp3.app.objects.{ EventClassGroupVariation, Group60Var2, Group60Var3, Group60Var4 }

// an event class: the object that reads it, and its options
final case class EventClassData(
    eventClass: EventClass,
    variation: EventClassGroupVariation,
    support: BooleanOption,
    numEventsGenerated: IntegerOption) {

  // a READ of the class's events
  def read(seq: Sequence): Message = Requests.readAllObjects(seq, variation)
}

object EventClassData {
  val class1: EventClassData =
    EventClassData(
      EventClass.Class1,
      Group60Var2,
      ClassOptions.class1.supported,
      ClassOptions.class1.numEventsGenerated)
  val class2: EventClassData =
    EventClassData(
      EventClass.Class2,
      Group60Var3,
      ClassOptions.class2.supported,
      ClassOptions.class2.numEventsGenerated)
  val class3: EventClassData =
    EventClassData(
      EventClass.Class3,
      Group60Var4,
      ClassOptions.class3.supported,
      ClassOptions.class3.numEventsGenerated)

  // the class numbered 1 to 3, as the options that assign points to a class give it
  def numbered(number: Int): EventClassData = number match {
    case 1 => class1
    case 2 => class2
    case 3 => class3
    case _ => throw new Exception(s"Unexpected event class: $number")
  }
}
