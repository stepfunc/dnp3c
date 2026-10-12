// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.parsing.{ ToBytes, UInt16LE, UInt8 }

object ObjectHeader {

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
