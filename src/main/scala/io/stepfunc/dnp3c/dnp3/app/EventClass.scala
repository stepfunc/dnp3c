// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

sealed case class EventClass(name: String) {
  final override def toString: String = name
}

object EventClass {
  object Class1 extends EventClass("Class 1")
  object Class2 extends EventClass("Class 2")
  object Class3 extends EventClass("Class 3")
  object All extends EventClass("Class 1, 2 and 3")
}
