// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.dnp3.{ LinkOverride, TestReporter }
import io.stepfunc.dnp3c.protocol.Clock
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt8 }

import scala.annotation.tailrec

// reads the frames of one connection; the FCB it expects starts at 1 for each connection
class ValidatingLinkFrameReader(
    reporter: TestReporter,
    parser: LinkFrameParser,
    local: UInt16LE,
    remote: UInt16LE,
    clock: Clock)(writer: LinkFrame => Unit) extends LinkFrameReader {

  // the FCB the next CONFIRMED_USER_DATA frame must carry
  private var expectedFcb = true

  def read(validator: FrameValidator, linkOverride: LinkOverride)(timeoutMs: Long): Option[List[Byte]] = {

    val deadline = clock.deadline(timeoutMs)

    def handle(frame: LinkFrame): Option[List[Byte]] = {

      val localAddress = linkOverride.destination match {
        case Some(address) => address
        case None => local
      }

      // check the addressing first
      if (frame.header.dest != localAddress) {
        throw new Exception("Received frame for unknown destination: " + frame.header.dest)
      }

      if (frame.header.src != remote) {
        throw new Exception("Received frame from unknown source: " + frame.header.src)
      }

      // run the validation (link status requests aren't part of the exchange being validated)
      if (frame.header.ctrl.func != LinkFunction.REQUEST_LINK_STATES) validator.validate(frame)

      def processConfirmed(frame: LinkFrame): List[Byte] = {
        if (frame.header.ctrl.fcb == expectedFcb) {
          expectedFcb = !expectedFcb
          writer(LinkFrame.headerOnly(Control.ack, remote, localAddress))
          frame.data
        } else {
          throw new Exception("Expected FCB: " + expectedFcb + " but received: " + frame.header.ctrl.fcb)
        }
      }

      frame.header.ctrl.func match {
        case LinkFunction.CONFIRMED_USER_DATA => Some(processConfirmed(frame))
        case LinkFunction.UNCONFIRMED_USER_DATA => Some(frame.data)
        case LinkFunction.REQUEST_LINK_STATES =>
          // e.g. a DNP3 keep-alive: answer it like a master would and keep reading
          writer(LinkFrame.headerOnly(Control(true, false, false, LinkFunction.LINK_STATUS), remote, localAddress))
          None
        case _ => throw new Exception("Received unexpected frame: " + frame)
      }
    }

    @tailrec
    def recurse(): Option[List[Byte]] = {
      val remain = clock.msLeft(deadline).toInt
      if (remain > 0) parser.read(reporter)(remain) match {
        case Some(frame) => handle(frame) match {
            case Some(bytes) => Some(bytes)
            case None => recurse()
          }
        case None =>
          None
      }
      else None
    }

    recurse()
  }

}
