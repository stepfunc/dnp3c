// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IndexListOption, OptionGroup, OptionSource, OptionTypes, TestOption }

object AttributeOptions extends OptionGroup {

  val supportedVariations: IndexListOption = OptionTypes.indexList(
    this,
    "supportedVariations",
    "Standard set (index 0) device attribute variations in the Device Profile, other than 254 and 255",
    Nil)

  val writableVariations: IndexListOption = OptionTypes.indexList(
    this,
    "writableVariations",
    "The supported device attribute variations that the DUT reports as read/write",
    Nil)

  override def prefix: String = "attributes"
  override def description: String = "Device attribute (group 0) options"

  override def options: List[TestOption] = List(supportedVariations, writableVariations)

  case class Config(supported: List[Int], writable: List[Int])

  def getConfig(src: OptionSource): Config = {
    val supported = src.get(supportedVariations).map(_.value)
    val writable = src.get(writableVariations).map(_.value)

    def check(option: IndexListOption, values: List[Int]): Unit = {
      values.find(v => v < 1 || v > 253).foreach(v => throw new Exception(s"${option.id}: $v is not in 1 to 253"))
      if (values.distinct != values) throw new Exception(s"${option.id} lists a variation more than once")
    }

    if (supported.isEmpty) throw new Exception(s"Configure ${supportedVariations.id} from the Device Profile")
    check(supportedVariations, supported)
    check(writableVariations, writable)
    writable.filterNot(supported.contains).foreach { v =>
      throw new Exception(s"${writableVariations.id}: $v is not in ${supportedVariations.id}")
    }
    Config(supported, writable)
  }
}
