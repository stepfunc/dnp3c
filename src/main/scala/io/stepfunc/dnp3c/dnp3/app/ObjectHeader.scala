// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.{ Parser, ToBytes, UInt16LE, UInt8 }

object ObjectHeader {

  /** a start/stop header of the variation's points from start to stop, without objects: 16-bit if twoByte */
  def startStop(variation: Variation, start: Int, stop: Int, twoByte: Boolean): ObjectHeader =
    if (twoByte) TwoByteStartStopHeader(variation, UInt16LE(start), UInt16LE(stop), FieldHeaderPayload.empty)
    else OneByteStartStopHeader(variation, UInt8(start), UInt8(stop), FieldHeaderPayload.empty)

  /** a header of the variation, by its qualifier code, with its range or count and objects, from the bytes after it */
  def parse(variation: Variation, qualifier: Byte, bytes: List[Byte]): Parser.Result[ObjectHeader] = {

    def insufficient(q: Qualifier): Nothing = throw new Exception("Not enough data for: " + q.name)

    def range(start: Int, stop: Int, objects: List[Byte]): Parser.Result[IterableHeaderPayload] = {
      if (stop < start) throw new Exception("stop < start")
      variation.parse(stop - start + 1, objects)
    }

    qualifier match {
      case Qualifier.ALL_OBJECTS.value => Parser.Result(AllObjectsHeader(variation), bytes)
      case Qualifier.ONE_BYTE_START_STOP.value =>
        bytes match {
          case b1 :: b2 :: objects =>
            val (start, stop) = (UInt8.fromByte(b1), UInt8.fromByte(b2))
            range(start.value, stop.value, objects).map(OneByteStartStopHeader(variation, start, stop, _))
          case _ => insufficient(Qualifier.ONE_BYTE_START_STOP)
        }
      case Qualifier.TWO_BYTE_START_STOP.value =>
        bytes match {
          case start1 :: start2 :: stop1 :: stop2 :: objects =>
            val (start, stop) = (UInt16LE(start1, start2), UInt16LE(stop1, stop2))
            range(start.value, stop.value, objects).map(TwoByteStartStopHeader(variation, start, stop, _))
          case _ => insufficient(Qualifier.TWO_BYTE_START_STOP)
        }
      case Qualifier.ONE_BYTE_COUNT.value =>
        bytes match {
          case b :: objects =>
            val count = UInt8.fromByte(b)
            variation.parse(count.value, objects).map(OneByteCountHeader(variation, count, _))
          case _ => insufficient(Qualifier.ONE_BYTE_COUNT)
        }
      case Qualifier.TWO_BYTE_COUNT.value =>
        bytes match {
          case lsb :: msb :: objects =>
            val count = UInt16LE(lsb, msb)
            variation.parse(count.value, objects).map(TwoByteCountHeader(variation, count, _))
          case _ => insufficient(Qualifier.TWO_BYTE_COUNT)
        }
      case Qualifier.ONE_BYTE_COUNT_AND_INDEX.value =>
        bytes match {
          case b :: objects =>
            val count = UInt8.fromByte(b)
            variation.parsePrefixedU8(count.value, objects).map(OneByteCountAndIndexHeader(variation, count, _))
          case _ => insufficient(Qualifier.ONE_BYTE_COUNT_AND_INDEX)
        }
      case Qualifier.TWO_BYTE_COUNT_AND_INDEX.value =>
        bytes match {
          case lsb :: msb :: objects =>
            val count = UInt16LE(lsb, msb)
            variation.parsePrefixedU16(count.value, objects).map(TwoByteCountAndIndexHeader(variation, count, _))
          case _ => insufficient(Qualifier.TWO_BYTE_COUNT_AND_INDEX)
        }
      case _ => throw new Exception("Unknown qualifier: " + Hex.prefixed(qualifier))
    }
  }

  def describe(start: Int, payload: HeaderPayload): Iterable[String] = payload.descriptions.zipWithIndex.map { x =>
    index(start + x._2) + " - " + x._1
  }

  private def index(i: Int): String = s"[$i]"
}

sealed trait ObjectHeader extends ToBytes {

  def variation: Variation

  def qualifier: Qualifier

  final override def toString: String = {
    val description = variation.key.name + ", " + qualifier.name
    extraDescription match {
      case Some(x) => description + ", " + x
      case None => description
    }
  }

  def contents: Iterable[String]

  def iterate: Seq[IndexedPayloadItem]

  final override def toBytes: List[Byte] = variation.key.bytes ::: List(qualifier.value) ::: dataBytes

  protected def extraDescription: Option[String]

  protected def dataBytes: List[Byte]
}

sealed trait ObjectHeaderWithPayload extends ObjectHeader {
  def payload: HeaderPayload
}

final case class AllObjectsHeader(variation: Variation) extends ObjectHeader {

  override def qualifier: Qualifier = Qualifier.ALL_OBJECTS

  override def extraDescription: Option[String] = None

  override def dataBytes: List[Byte] = Nil

  override def contents: Iterable[String] = Nil

  override def iterate: Seq[IndexedPayloadItem] = Nil
}

final case class OneByteStartStopHeader(variation: Variation, start: UInt8, stop: UInt8, payload: IterableHeaderPayload)
    extends ObjectHeaderWithPayload {

  override def qualifier: Qualifier = Qualifier.ONE_BYTE_START_STOP

  override def extraDescription: Option[String] = Some("start: " + start.value + " stop: " + stop.value)

  override def dataBytes: List[Byte] = start.toBytes ::: stop.toBytes ::: payload.toBytes

  override def contents: Iterable[String] = ObjectHeader.describe(start.value, payload)

  override def iterate: Seq[IndexedPayloadItem] =
    payload.iterate.zipWithIndex.map(x => IndexedPayloadItem(x._2 + start.value, x._1))
}

final case class TwoByteStartStopHeader(
    variation: Variation,
    start: UInt16LE,
    stop: UInt16LE,
    payload: IterableHeaderPayload) extends ObjectHeaderWithPayload {

  override def qualifier: Qualifier = Qualifier.TWO_BYTE_START_STOP

  override def extraDescription: Option[String] = Some("start: " + start.value + " stop: " + stop.value)

  override def dataBytes: List[Byte] = start.toBytes ::: stop.toBytes ::: payload.toBytes

  override def contents: Iterable[String] = ObjectHeader.describe(start.value, payload)

  override def iterate: Seq[IndexedPayloadItem] =
    payload.iterate.zipWithIndex.map(x => IndexedPayloadItem(x._2 + start.value, x._1))
}

final case class OneByteCountHeader(variation: Variation, count: UInt8, payload: IterableHeaderPayload)
    extends ObjectHeaderWithPayload {

  override def qualifier: Qualifier = Qualifier.ONE_BYTE_COUNT

  override def extraDescription: Option[String] = Some("count: " + count.value)

  override def dataBytes: List[Byte] = count.toBytes ::: payload.toBytes

  override def contents: Iterable[String] = payload.descriptions

  override def iterate: Seq[IndexedPayloadItem] = payload.iterate.map(x => IndexedPayloadItem(x))
}

final case class TwoByteCountHeader(variation: Variation, count: UInt16LE, payload: IterableHeaderPayload)
    extends ObjectHeaderWithPayload {

  override def qualifier: Qualifier = Qualifier.TWO_BYTE_COUNT

  override def extraDescription: Option[String] = Some("count: " + count.value)

  override def dataBytes: List[Byte] = count.toBytes ::: payload.toBytes

  override def contents: Iterable[String] = payload.descriptions

  override def iterate: Seq[IndexedPayloadItem] = payload.iterate.map(x => IndexedPayloadItem(x))
}

sealed trait CountAndIndexPrefixHeader extends ObjectHeaderWithPayload {

  def variation: Variation

  def payload: PrefixedPayload

}

final case class OneByteCountAndIndexHeader(variation: Variation, count: UInt8, payload: PrefixedPayload)
    extends CountAndIndexPrefixHeader {

  override def qualifier: Qualifier = Qualifier.ONE_BYTE_COUNT_AND_INDEX

  override def extraDescription: Option[String] = Some("count: " + count.value)

  override def dataBytes: List[Byte] = count.toBytes ::: payload.toBytes

  override def contents: Iterable[String] = payload.descriptions

  override def iterate: Seq[IndexedPayloadItem] = payload.iterate
}

final case class TwoByteCountAndIndexHeader(variation: Variation, count: UInt16LE, payload: PrefixedPayload)
    extends CountAndIndexPrefixHeader {

  override def qualifier: Qualifier = Qualifier.TWO_BYTE_COUNT_AND_INDEX

  override def extraDescription: Option[String] = Some("count: " + count.value)

  override def dataBytes: List[Byte] = count.toBytes ::: payload.toBytes

  override def contents: Iterable[String] = payload.descriptions

  override def iterate: Seq[IndexedPayloadItem] = payload.iterate
}
