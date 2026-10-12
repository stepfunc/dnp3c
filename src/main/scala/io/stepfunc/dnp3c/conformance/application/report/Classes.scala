// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

case class Clazz(id: String)

object Classes {

  /// ----- Classes for the main page ------

  def level(i: Int) = Clazz(s"level$i")

  val pass = Clazz("pass")
  val fail = Clazz("fail")
  val skip = Clazz("skip")
  val noResult = Clazz("no-result")
  val section = Clazz("section")
  val procedure = Clazz("procedure")
  val incomplete = Clazz("incomplete")
  val unimplemented = Clazz("unimplemented")

  /// ----- Classes log pages ------

  val plugin = Clazz("plugin")
  val note = Clazz("note")
  val info = Clazz("info")
  val warn = Clazz("warn")
  val error = Clazz("error")
  val receive = Clazz("receive")
  val transmit = Clazz("transmit")

  def details(level: Int) = Clazz(s"detail-level-$level")

  val dataLevel1 = Clazz("data-level-1")
  val dataLevel2 = Clazz("data-level-2")
  val dataLevel3 = Clazz("data-level-3")
  val dataLevel4 = Clazz("data-level-4")

}
