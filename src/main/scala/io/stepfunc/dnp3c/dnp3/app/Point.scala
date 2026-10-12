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

abstract class MeasurementBuild extends PointBuilder {
  var flags: Option[Byte] = None
  var timestamp: Option[Timestamp] = None

  override def onFlags(flags: Byte): Unit = {
    this.flags = Some(flags)
  }

  override def onTimestamp(timestamp: Timestamp): Unit = {
    this.timestamp = Some(timestamp)
  }
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

class BinaryBuilder(pointType: PointType) extends MeasurementBuild {
  var value: Option[Boolean] = None

  override def onBooleanValue(value: Boolean): Unit = {
    this.value = Some(value)
  }

  override def build(): Point = {
    this.value match {
      case Some(value) => BinaryPoint(value, flags, timestamp, pointType)
      case None => throw new Exception("Missing boolean value")
    }
  }
}

class DoubleBitBuilder(pointType: PointType) extends MeasurementBuild {
  var value: Option[DoubleBit] = None

  override def onDoubleBitValue(value: DoubleBit): Unit = {
    this.value = Some(value)
  }

  override def build(): Point = {
    this.value match {
      case Some(value) => DoubleBitPoint(value, flags, timestamp, pointType)
      case None => throw new Exception("Missing double-bit value")
    }
  }
}

class CounterBuilder(pointType: PointType, isFrozen: Boolean) extends MeasurementBuild {
  var value: Option[Long] = None

  override def onIntegerValue(value: Long): Unit = {
    this.value = Some(value)
  }

  override def build(): Point = {
    this.value match {
      case Some(value) => CounterPoint(value, isFrozen, flags, timestamp, pointType)
      case None => throw new Exception("Missing counter value")
    }
  }
}

class AnalogBuilder(pointType: PointType, isFrozen: Boolean) extends MeasurementBuild {
  var value: Option[Double] = None

  override def onIntegerValue(value: Long): Unit = {
    this.value = Some(value.toDouble)
  }

  override def onFloatingValue(value: Double): Unit = {
    this.value = Some(value)
  }

  override def build(): Point = {
    this.value match {
      case Some(value) => AnalogPoint(value, isFrozen, flags, timestamp, pointType)
      case None => throw new Exception("Missing analog value")
    }
  }
}

class BinaryOutputStatusBuilder(pointType: PointType) extends MeasurementBuild {
  var value: Option[Boolean] = None

  override def onBooleanValue(value: Boolean): Unit = {
    this.value = Some(value)
  }

  override def build(): Point = {
    this.value match {
      case Some(value) => BinaryOutputStatusPoint(value, flags, timestamp, pointType)
      case None => throw new Exception("Missing boolean value")
    }
  }
}

class AnalogOutputStatusBuilder(pointType: PointType) extends MeasurementBuild {
  var value: Option[Double] = None

  override def onIntegerValue(value: Long): Unit = {
    this.value = Some(value.toDouble)
  }

  override def onFloatingValue(value: Double): Unit = {
    this.value = Some(value)
  }

  override def build(): Point = {
    this.value match {
      case Some(value) => AnalogOutputStatusPoint(value, flags, timestamp, pointType)
      case None => throw new Exception("Missing analog value")
    }
  }
}

// creates a fresh point builder for each object a variation parses
final class PointBuilderFactory(builder: () => PointBuilder) {
  def create(): PointBuilder = builder()
}

val OtherBuilder: PointBuilderFactory = PointBuilderFactory(() => new OtherPointBuilder())
val TimeOffsetBuilder: PointBuilderFactory = PointBuilderFactory(() => new TimeOffsetPointBuilder())
val StaticBinaryBuilder: PointBuilderFactory = PointBuilderFactory(() => new BinaryBuilder(PointType.Static))
val EventBinaryBuilder: PointBuilderFactory = PointBuilderFactory(() => new BinaryBuilder(PointType.Event))
val StaticDoubleBitBuilder: PointBuilderFactory = PointBuilderFactory(() => new DoubleBitBuilder(PointType.Static))
val EventDoubleBitBuilder: PointBuilderFactory = PointBuilderFactory(() => new DoubleBitBuilder(PointType.Event))
val StaticCounterBuilder: PointBuilderFactory = PointBuilderFactory(() => new CounterBuilder(PointType.Static, false))
val EventCounterBuilder: PointBuilderFactory = PointBuilderFactory(() => new CounterBuilder(PointType.Event, false))
val StaticFrozenCounterBuilder: PointBuilderFactory =
  PointBuilderFactory(() => new CounterBuilder(PointType.Static, true))
val EventFrozenCounterBuilder: PointBuilderFactory =
  PointBuilderFactory(() => new CounterBuilder(PointType.Event, true))
val StaticAnalogBuilder: PointBuilderFactory = PointBuilderFactory(() => new AnalogBuilder(PointType.Static, false))
val EventAnalogBuilder: PointBuilderFactory = PointBuilderFactory(() => new AnalogBuilder(PointType.Event, false))
val StaticFrozenAnalogBuilder: PointBuilderFactory =
  PointBuilderFactory(() => new AnalogBuilder(PointType.Static, true))
val EventFrozenAnalogBuilder: PointBuilderFactory = PointBuilderFactory(() => new AnalogBuilder(PointType.Event, true))
val StaticBinaryOutputStatusBuilder: PointBuilderFactory =
  PointBuilderFactory(() => new BinaryOutputStatusBuilder(PointType.Static))
val EventBinaryOutputStatusBuilder: PointBuilderFactory =
  PointBuilderFactory(() => new BinaryOutputStatusBuilder(PointType.Event))
val StaticAnalogOutputStatusBuilder: PointBuilderFactory =
  PointBuilderFactory(() => new AnalogOutputStatusBuilder(PointType.Static))
val EventAnalogOutputStatusBuilder: PointBuilderFactory =
  PointBuilderFactory(() => new AnalogOutputStatusBuilder(PointType.Event))
