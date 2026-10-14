// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

sealed trait PointType
object PointType {
  case object Static extends PointType
  case object Event extends PointType
}

// isRelative: the point came from a relative-time variation. Once the message is iterated, value already includes
// the CTO's time.
case class Timestamp(value: Long, isRelative: Boolean = false)

case class GenIndexedPoint[T <: MeasurementPoint](idx: Int, point: T)

sealed trait Point

object Other extends Point
case class TimeOffset(offsetMs: Long) extends Point

sealed abstract class MeasurementPoint(
    val pointType: PointType,
    val flags: Option[Byte],
    val timestamp: Option[Timestamp]) extends Point {
  def copy_with_timestamp(timestamp: Option[Timestamp] = None): MeasurementPoint
}
case class BinaryPoint(
    value: Boolean,
    override val flags: Option[Byte],
    override val timestamp: Option[Timestamp],
    override val pointType: PointType) extends MeasurementPoint(pointType, flags, timestamp) {
  override def copy_with_timestamp(timestamp: Option[Timestamp]): BinaryPoint = {
    this.copy(timestamp = timestamp)
  }
}
case class DoubleBitPoint(
    value: DoubleBit,
    override val flags: Option[Byte],
    override val timestamp: Option[Timestamp],
    override val pointType: PointType) extends MeasurementPoint(pointType, flags, timestamp) {
  override def copy_with_timestamp(timestamp: Option[Timestamp]): DoubleBitPoint = {
    this.copy(timestamp = timestamp)
  }
}
case class CounterPoint(
    value: Long,
    isFrozen: Boolean,
    override val flags: Option[Byte],
    override val timestamp: Option[Timestamp],
    override val pointType: PointType) extends MeasurementPoint(pointType, flags, timestamp) {
  override def copy_with_timestamp(timestamp: Option[Timestamp]): CounterPoint = {
    this.copy(timestamp = timestamp)
  }
}
case class AnalogPoint(
    value: Double,
    isFrozen: Boolean,
    override val flags: Option[Byte],
    override val timestamp: Option[Timestamp],
    override val pointType: PointType) extends MeasurementPoint(pointType, flags, timestamp) {
  override def copy_with_timestamp(timestamp: Option[Timestamp]): AnalogPoint = {
    this.copy(timestamp = timestamp)
  }
}
case class BinaryOutputStatusPoint(
    value: Boolean,
    override val flags: Option[Byte],
    override val timestamp: Option[Timestamp],
    override val pointType: PointType) extends MeasurementPoint(pointType, flags, timestamp) {
  override def copy_with_timestamp(timestamp: Option[Timestamp]): BinaryOutputStatusPoint = {
    this.copy(timestamp = timestamp)
  }
}
case class AnalogOutputStatusPoint(
    value: Double,
    override val flags: Option[Byte],
    override val timestamp: Option[Timestamp],
    override val pointType: PointType) extends MeasurementPoint(pointType, flags, timestamp) {
  override def copy_with_timestamp(timestamp: Option[Timestamp]): AnalogOutputStatusPoint = {
    this.copy(timestamp = timestamp)
  }
}

trait PointBuilder {
  def onFlags(flags: Byte): Unit
  def onTimestamp(timestamp: Timestamp): Unit

  def onBooleanValue(value: Boolean): Unit = {
    throw new Exception("This point type does not support boolean values")
  }
  def onDoubleBitValue(value: DoubleBit): Unit = {
    throw new Exception("This point type does not support double-bit values")
  }
  def onIntegerValue(value: Long): Unit = {
    throw new Exception("This point type does not support integer values")
  }
  def onFloatingValue(value: Double): Unit = {
    throw new Exception("This point type does not support floating-point values")
  }

  def build(): Point
}

class OtherPointBuilder extends PointBuilder {
  override def onFlags(flags: Byte): Unit = {}
  override def onTimestamp(timestamp: Timestamp): Unit = {}

  override def onBooleanValue(value: Boolean): Unit = {}
  override def onDoubleBitValue(value: DoubleBit): Unit = {}
  override def onIntegerValue(value: Long): Unit = {}
  override def onFloatingValue(value: Double): Unit = {}

  override def build(): Point = Other
}

class TimeOffsetPointBuilder extends PointBuilder {
  var offsetMs: Option[Long] = None

  override def onFlags(flags: Byte): Unit = {}
  override def onTimestamp(timestamp: Timestamp): Unit = {
    this.offsetMs = Some(timestamp.value)
  }

  override def build(): Point = {
    this.offsetMs match {
      case Some(value) => TimeOffset(value)
      case None => throw new Exception("Missing timestamp value")
    }
  }
}

// a measurement's value, flags and time, as its fields are parsed, made into a point once they all have been; a
// builder takes the values of the kinds its overrides accept
private abstract class MeasurementBuilder[V](kind: String, point: (V, Option[Byte], Option[Timestamp]) => Point)
    extends PointBuilder {
  private var flags: Option[Byte] = None
  private var timestamp: Option[Timestamp] = None
  private var value: Option[V] = None

  override def onFlags(flags: Byte): Unit = this.flags = Some(flags)
  override def onTimestamp(timestamp: Timestamp): Unit = this.timestamp = Some(timestamp)
  protected def onValue(value: V): Unit = this.value = Some(value)

  override def build(): Point = value match {
    case Some(v) => point(v, flags, timestamp)
    case None => throw new Exception(s"Missing $kind value")
  }
}

private class BooleanValueBuilder(point: (Boolean, Option[Byte], Option[Timestamp]) => Point)
    extends MeasurementBuilder[Boolean]("boolean", point) {
  override def onBooleanValue(value: Boolean): Unit = onValue(value)
}

private class DoubleBitValueBuilder(point: (DoubleBit, Option[Byte], Option[Timestamp]) => Point)
    extends MeasurementBuilder[DoubleBit]("double-bit", point) {
  override def onDoubleBitValue(value: DoubleBit): Unit = onValue(value)
}

private class IntegerValueBuilder(point: (Long, Option[Byte], Option[Timestamp]) => Point)
    extends MeasurementBuilder[Long]("counter", point) {
  override def onIntegerValue(value: Long): Unit = onValue(value)
}

// an integer value is taken as a floating-point one
private class FloatingValueBuilder(point: (Double, Option[Byte], Option[Timestamp]) => Point)
    extends MeasurementBuilder[Double]("analog", point) {
  override def onIntegerValue(value: Long): Unit = onValue(value.toDouble)
  override def onFloatingValue(value: Double): Unit = onValue(value)
}

// creates a fresh point builder for each object a variation parses, for its static values or its events
final class PointBuilderFactory(builder: PointType => PointBuilder) {
  def create(pointType: PointType): PointBuilder = builder(pointType)
}

val OtherBuilder: PointBuilderFactory = PointBuilderFactory(_ => new OtherPointBuilder())
val TimeOffsetBuilder: PointBuilderFactory = PointBuilderFactory(_ => new TimeOffsetPointBuilder())
val BinaryBuilder: PointBuilderFactory = PointBuilderFactory(t => new BooleanValueBuilder(BinaryPoint(_, _, _, t)))
val DoubleBitBuilder: PointBuilderFactory =
  PointBuilderFactory(t => new DoubleBitValueBuilder(DoubleBitPoint(_, _, _, t)))
val CounterBuilder: PointBuilderFactory =
  PointBuilderFactory(t => new IntegerValueBuilder(CounterPoint(_, false, _, _, t)))
val FrozenCounterBuilder: PointBuilderFactory =
  PointBuilderFactory(t => new IntegerValueBuilder(CounterPoint(_, true, _, _, t)))
val AnalogBuilder: PointBuilderFactory =
  PointBuilderFactory(t => new FloatingValueBuilder(AnalogPoint(_, false, _, _, t)))
val FrozenAnalogBuilder: PointBuilderFactory =
  PointBuilderFactory(t => new FloatingValueBuilder(AnalogPoint(_, true, _, _, t)))
val BinaryOutputStatusBuilder: PointBuilderFactory =
  PointBuilderFactory(t => new BooleanValueBuilder(BinaryOutputStatusPoint(_, _, _, t)))
val AnalogOutputStatusBuilder: PointBuilderFactory =
  PointBuilderFactory(t => new FloatingValueBuilder(AnalogOutputStatusPoint(_, _, _, t)))
