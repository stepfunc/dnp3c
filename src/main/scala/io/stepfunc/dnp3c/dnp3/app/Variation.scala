// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.parsing.{ Field, Parser }

import scala.collection.immutable.Map

object Variation {

  case class Key(group: Byte, variation: Byte) {
    def bytes = List(group, variation)
    def name: String = "g" + (0xFF & group) + "v" + (0xFF & variation)
    override def toString: String = name
  }

  val list: List[Variation] = Group.list.flatMap(_.variations)

  private val lookup: Map[Variation.Key, Variation] = {
    list.foldLeft(Map.empty[Variation.Key, Variation]) { (map, x) =>
      if (map.contains(x.key)) throw new Exception("Duplicate variation: " + x.key)
      map + (x.key -> x)
    }
  }

  def get(group: Byte, variation: Byte): Variation = {
    val key = Variation.Key(group, variation)
    lookup.get(key) match {
      case Some(x) => x
      case None => throw new Exception("Unknown variation: " + key)
    }
  }

  def mock(groupId: Byte, variationId: Byte): Variation = new Variation {

    override def id: Byte = variationId

    override def group: Group = {
      val self = this
      new Group {
        override def id: Byte = groupId
        override def variations: List[Variation] = List(self)
      }
    }

    override def objectType: ObjectType = Unknown

    override def minSubsetLevel: SubsetLevel = SubsetLevel.None
  }
}

trait Variation {

  def id: Byte

  def group: Group

  def objectType: ObjectType

  def key: Variation.Key = Variation.Key(group.id, id)

  def minSubsetLevel: SubsetLevel

  override def toString: String = key.toString

  // count objects, as a start/stop or count header carries them
  def parse(count: Int, bytes: List[Byte]): Parser.Result[IterableHeaderPayload] = {
    throw new Exception(key.name + " may not be used with a start/stop or count header")
  }

  def parsePrefixedU8(count: Int, bytes: List[Byte]): Parser.Result[PrefixedPayload.U8] = {
    throw new Exception(key.name + " may not be used with a one-byte count and prefix header")
  }

  def parsePrefixedU16(count: Int, bytes: List[Byte]): Parser.Result[PrefixedPayload.U16] = {
    throw new Exception(key.name + " may not be used with a two-byte count and prefix header")
  }
}

abstract case class EmptyVariation(group: Group, id: Byte) extends Variation

abstract case class SingleBitField(group: Group, id: Byte, isOutputStatus: Boolean) extends Variation {

  override def parse(count: Int, bytes: List[Byte]): Parser.Result[IterableHeaderPayload] =
    BitFields.single(count).parse(bytes).map(values =>
      BitFieldHeaderPayload(values.map(BooleanValue.apply), isOutputStatus))
}

abstract case class DoubleBitField(group: Group, id: Byte) extends Variation {

  override def parse(count: Int, bytes: List[Byte]): Parser.Result[IterableHeaderPayload] =
    BitFields.double(count).parse(bytes).map(DoubleBitFieldHeaderPayload.apply)
}

// a fixed sequence of fields; a field may carry its own length, as device attributes do
abstract case class FixedSize(
    group: Group,
    id: Byte,
    fields: List[FieldPointParser],
    pointBuilderFactory: PointBuilderFactory = OtherBuilder) extends Variation {

  private def parser(pointBuilder: PointBuilder): Parser[Field.Collection] = {
    Parser.sequence(fields.map(p => new FieldPointParserAdapter(p, pointBuilder)))(Field.Collection.apply)
  }
  private def parserPrefixU8(pointBuilder: PointBuilder): Parser[Prefix.U8Fields] =
    Prefix.parserU8(parser(pointBuilder))
  private def parserPrefixU16(pointBuilder: PointBuilder): Parser[Prefix.U16Fields] =
    Prefix.parserU16(parser(pointBuilder))

  // its points' type: an event variation's are events, and any other's static
  private def pointType: PointType = if (objectType == Event) PointType.Event else PointType.Static

  // parses count objects in turn, each with a fresh point builder, keeping their order
  private def parseObjects[A](count: Int, bytes: List[Byte])(
      parseOne: (PointBuilder, List[Byte]) => Parser.Result[A]): Parser.Result[List[A]] = {
    val end = (1 to count).foldLeft(Parser.start[A](bytes)) { (s, _) =>
      val result = parseOne(pointBuilderFactory.create(pointType), s.remainder)
      Parser.Result[List[A]](result.value :: s.value, result.remainder)
    }
    end.map(_.reverse)
  }

  override def parse(count: Int, bytes: List[Byte]): Parser.Result[IterableHeaderPayload] =
    parseObjects(count, bytes) { (builder, input) =>
      parser(builder).parse(input).map(e => FieldHeaderPayloadObject(e, builder.build()))
    }.map(FieldHeaderPayload.apply)

  override def parsePrefixedU8(count: Int, bytes: List[Byte]): Parser.Result[PrefixedPayload.U8] =
    parseObjects(count, bytes) { (builder, input) =>
      parserPrefixU8(builder).parse(input).map(e => Prefix.U8(e, builder.build()))
    }.map(PrefixedPayload.U8.apply)

  override def parsePrefixedU16(count: Int, bytes: List[Byte]): Parser.Result[PrefixedPayload.U16] =
    parseObjects(count, bytes) { (builder, input) =>
      parserPrefixU16(builder).parse(input).map(e => Prefix.U16(e, builder.build()))
    }.map(PrefixedPayload.U16.apply)
}

trait FixedSizeSerializable {
  def variation: Variation
  def toFixedSizeObject: Field.Collection
}

object TypeTraits {

  trait Static extends Variation {
    final def objectType: ObjectType = Static
  }

  trait StaticVariationZero extends Variation {
    final def objectType: ObjectType = StaticVariationZero
  }

  trait Event extends Variation {
    final def objectType: ObjectType = Event
  }

  trait EventVariationZero extends Variation {
    final def objectType: ObjectType = EventVariationZero
  }

  trait Command extends Variation {
    final def objectType: ObjectType = Command
  }

  trait Information extends Variation {
    final def objectType: ObjectType = Information
  }

  trait CommonTimeOfOccurrence extends Variation {
    final def objectType: ObjectType = CommonTimeOfOccurrence
  }

  trait Attribute extends Variation {
    final def objectType: ObjectType = Attribute
  }

  // Subset Level traits
  trait Level1 extends Variation {
    final def minSubsetLevel: SubsetLevel = SubsetLevel.One
  }

  trait Level2 extends Variation {
    final def minSubsetLevel: SubsetLevel = SubsetLevel.Two
  }

  trait Level3 extends Variation {
    final def minSubsetLevel: SubsetLevel = SubsetLevel.Three
  }

  trait Level4 extends Variation {
    final def minSubsetLevel: SubsetLevel = SubsetLevel.Four
  }

  trait None extends Variation {
    final def minSubsetLevel: SubsetLevel = SubsetLevel.None
  }

}
