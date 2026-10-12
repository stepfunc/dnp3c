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
