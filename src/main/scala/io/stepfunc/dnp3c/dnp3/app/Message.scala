// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.{ Parser, ToBytes }

import scala.annotation.tailrec

object Message {

  // a request from the master (the harness): first and final fragment, no confirmation asked, no IIN
  def request(seq: Sequence, function: AppFunction, headers: List[ObjectHeader]): Message =
    Message(Control.request(seq), function, None, headers)

  def parse(bytes: List[Byte]): Message = {

    def parseResponse(ctrl: Control, function: AppFunction, bytes: List[Byte]): Message = {

      def parseIIN(bytes: List[Byte]): Parser.Result[IIN] = bytes match {
        case lsb :: msb :: remainder => Parser.Result(IIN(lsb, msb), remainder)
        case _ => throw new Exception("Insufficient bytes for IIN")
      }

      val result = parseIIN(bytes)
      Message(ctrl, function, Some(result.value), parseObjectHeaders(result.remainder))

    }

    bytes match {
      case ctrl :: func :: remainder =>
        val control = Control(ctrl)
        val function = AppFunction.get(func)
        if (function.isResponse) {
          parseResponse(control, function, remainder)
        } else {
          Message(control, function, None, parseObjectHeaders(remainder))
        }
      case _ =>
        throw new Exception("Insufficient bytes for message: " + bytes.length)
    }
  }

  def parseObjectHeaders(bytes: List[Byte]): List[ObjectHeader] = {

    @tailrec
    def recurse(headers: List[ObjectHeader], remainder: List[Byte]): List[ObjectHeader] = {
      remainder match {
        case Nil => headers
        case g :: v :: q :: remain =>
          val result = ObjectHeader.parse(Variation.get(g, v), q, remain)
          recurse(result.value :: headers, result.remainder)
        case _ =>
          throw new Exception("Insufficient bytes for object header: " + remainder.length)
      }
    }

    recurse(Nil, bytes).reverse
  }
}

case class Message(ctrl: Control, function: AppFunction, iin: Option[IIN], headers: List[ObjectHeader])
    extends ToBytes {

  override def toBytes: List[Byte] = {
    ctrl.toByte :: function.value :: (iin.map(_.toBytes).getOrElse(Nil) ::: headers.flatMap(_.toBytes))
  }

  override def toString: String = {
    val s1 = function.name + "(" + Hex.prefixed(function.value) + ") " + ctrl.toString
    iin match {
      case Some(x) => s1 + " " + x.toString
      case None => s1
    }
  }

  // the measurement points, in order, with relative times resolved against the CTO before them
  def iterate: Seq[GenIndexedPoint[MeasurementPoint]] = {
    var result: List[GenIndexedPoint[MeasurementPoint]] = Nil

    var currentOffset: Option[Long] = None
    for (header <- headers.flatMap(_.iterate)) {
      header.point match {
        case point: TimeOffset => currentOffset = Some(point.offsetMs)
        case point: MeasurementPoint =>
          val timestamp: Option[Timestamp] = point.timestamp match {
            // Update relative timestamps
            case Some(timestamp) if timestamp.isRelative =>
              val offset = currentOffset.getOrElse(throw new Exception("Relative time missing CTO/delay object"))
              Some(Timestamp(timestamp.value + offset, isRelative = true))
            case _ => point.timestamp
          }

          // Extract index
          val idx = header.idx match {
            case Indexed(idx) => idx
            case NotIndexed => throw new Exception("Measurement points should have an index")
          }

          result = GenIndexedPoint(idx, point.copy_with_timestamp(timestamp)) :: result
        case _ =>
      }
    }

    result.reverse
  }

  // the measurement points of one type (static values or events), with their CTO-resolved times
  def measurements(pointType: PointType): Seq[GenIndexedPoint[MeasurementPoint]] =
    iterate.filter(_.point.pointType == pointType)
}
