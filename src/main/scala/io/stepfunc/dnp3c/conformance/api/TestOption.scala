// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.protocol.Hex

sealed trait TestOption {

  final def id: String = group.prefix + "." + name

  protected def name: String

  def group: OptionGroup

  def description: String

  def defaultAsString: String

  // a required option has no default: a configuration must give it a value before procedures can use it
  def required: Boolean = false
}

trait HasDefault[T] {
  def default: T
  def defaultAsString: String = default.toString
}

sealed trait TypedTestOption[T] extends TestOption with HasDefault[T] {

  // the option's value type, which OptionSource.get returns
  type Value = T

  /**
   * the value of the option, throwing an exception if the supplied value is invalid
   * @param s String value of option
   */
  def parse(s: String): T
}
trait StringOption extends TypedTestOption[String]
trait BooleanOption extends TypedTestOption[scala.Boolean] {
  def unary_! : OptionCondition = !OptionCondition(this)
}
trait HexByteOption extends TypedTestOption[Byte]
trait IntegerOption extends TypedTestOption[Int]
trait IndexOption extends TypedTestOption[Index]
trait OptionalIndexOption extends TypedTestOption[Option[Index]]
trait IndexListOption extends TypedTestOption[List[Index]]
// an integer that has no default, so None until a configuration sets it
trait RequiredIntegerOption extends TypedTestOption[Option[Int]] {
  final override def required: Boolean = true
}

object OptionTypes {

  def string(group: OptionGroup, name: String, default: String, description: String): StringOption =
    StringOptionImpl(group, name, default, description)
  def boolean(group: OptionGroup, name: String, default: Boolean, description: String): BooleanOption =
    BooleanOptionImpl(group, name, default, description)
  def integer(
      group: OptionGroup,
      name: String,
      default: Int,
      desc: String,
      min: Option[Int],
      max: Option[Int]): IntegerOption = IntegerOptionImpl(group, name, default, desc, min, max)
  // an index no larger than max: Index.maxOneOctet for one that procedures send with 1-octet indexes
  def index(
      group: OptionGroup,
      name: String,
      default: Index,
      description: String,
      max: Index = Index.max): IndexOption =
    IndexOptionImpl(group, name, default, description, max)
  def optionalIndex(
      group: OptionGroup,
      name: String,
      default: Option[Index],
      description: String,
      max: Index = Index.max): OptionalIndexOption = OptionalIndexOptionImpl(group, name, default, description, max)
  def indexList(
      group: OptionGroup,
      name: String,
      description: String,
      default: List[Index] = (0 to 9).map(Index.apply).toList): IndexListOption =
    IndexListOptionImpl(group, name, description, default)
  // one of the allowed values, which a configuration must set: there's no default
  def requiredInteger(group: OptionGroup, name: String, desc: String, allowed: List[Int]): RequiredIntegerOption =
    RequiredIntegerOptionImpl(group, name, desc, allowed)
  // an integer of at least min, which a configuration must set: there's no default
  def requiredIntegerAtLeast(group: OptionGroup, name: String, desc: String, min: Int): RequiredIntegerOption =
    RequiredMinimumOptionImpl(group, name, desc, min)
  def hexByteInSet(group: OptionGroup, name: String, default: Byte, desc: String, allowed: Set[Byte]): HexByteOption =
    HexByteInSetImpl(group, name, default, desc, allowed)

  private case class StringOptionImpl(group: OptionGroup, name: String, default: String, description: String)
      extends StringOption {
    override def parse(s: String): String = s.trim
  }

  private case class BooleanOptionImpl(group: OptionGroup, name: String, default: Boolean, description: String)
      extends BooleanOption {
    override def parse(s: String): Boolean = Parse.boolean(s)
  }

  private case class IntegerOptionImpl(
      group: OptionGroup,
      name: String,
      default: Int,
      desc: String,
      min: Option[Int],
      max: Option[Int]) extends IntegerOption {

    override def description: String = {
      val minString = min.map(m => "min: " + m)
      val maxString = max.map(m => "max: " + m)
      List(Some(desc), minString, maxString).flatten[String].mkString(" ")
    }

    override def parse(s: String): Int = {
      val value = Parse.integer(s)
      min.foreach(x => if (value < x) throw new Exception(s"$value is less than the minimum, $x"))
      max.foreach(x => if (value > x) throw new Exception(s"$value is more than the maximum, $x"))
      value
    }
  }

  private case class IndexOptionImpl(group: OptionGroup, name: String, default: Index, desc: String, max: Index)
      extends IndexOption {

    override def description: String = withMax(desc, max)

    override def parse(s: String): Index = Parse.index(s, max)

    override def defaultAsString: String = default.value.toString
  }

  private case class OptionalIndexOptionImpl(
      group: OptionGroup,
      name: String,
      default: Option[Index],
      desc: String,
      max: Index) extends OptionalIndexOption {

    override def description: String = withMax(desc, max)

    override def parse(s: String): Option[Index] = Parse.optionalIndex(s, max)

    override def defaultAsString: String = default match {
      case Some(idx) => idx.value.toString
      case None => "null"
    }
  }

  private case class RequiredIntegerOptionImpl(group: OptionGroup, name: String, desc: String, allowed: List[Int])
      extends RequiredIntegerOption {

    override def default: Option[Int] = None

    override def description: String = s"$desc. Required, with no default: one of ${allowed.mkString(", ")}"

    override def parse(s: String): Option[Int] = {
      val value = Parse.integer(s)
      if (!allowed.contains(value)) throw new Exception(s"$value isn't one of ${allowed.mkString(", ")}")
      Some(value)
    }

    override def defaultAsString: String = ""
  }

  private case class RequiredMinimumOptionImpl(group: OptionGroup, name: String, desc: String, min: Int)
      extends RequiredIntegerOption {

    override def default: Option[Int] = None

    override def description: String = s"$desc. Required, with no default, where it's used: at least $min"

    override def parse(s: String): Option[Int] = {
      val value = Parse.integer(s)
      if (value < min) throw new Exception(s"$value is less than the minimum, $min")
      Some(value)
    }

    override def defaultAsString: String = ""
  }

  private def withMax(desc: String, max: Index): String =
    if (max == Index.max) desc else s"$desc max: ${max.value}"

  private case class IndexListOptionImpl(group: OptionGroup, name: String, desc: String, default: List[Index])
      extends IndexListOption {

    override def description: String = desc

    override def parse(s: String): List[Index] = Parse.indexList(s)

    // a contiguous range is written as "first ... last", anything else as a comma separated list
    override def defaultAsString: String = default.map(_.value) match {
      case values @ (first :: _ :: _) if values == (first to values.last).toList => s"$first ... ${values.last}"
      case values => values.mkString(", ")
    }

  }

  private case class HexByteInSetImpl(group: OptionGroup, name: String, default: Byte, desc: String, allowed: Set[Byte])
      extends HexByteOption {

    private def setToString: String =
      allowed.toList.sortBy(java.lang.Byte.toUnsignedInt).map(Hex.prefixed).mkString(", ")

    override def description: String = s"$desc, one of: $setToString"

    override def defaultAsString: String = Hex.prefixed(default)

    override def parse(s: String): Byte = {
      val value = Parse.hexByte(s)
      if (!allowed.contains(value)) throw new Exception(s"${Hex.prefixed(value)} is not one of: $setToString")
      value
    }
  }
}
