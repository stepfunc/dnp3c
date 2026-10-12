// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.transport

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.link.LinkFrameReader
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, TestReporter }
import io.stepfunc.dnp3c.protocol.Clock

class TransportReader(reader: LinkFrameReader, reporter: TestReporter, clock: Clock) {

  /**
   * Read a Full ASDU or timeout trying
   *
   * @param timeoutMs timeout
   * @param validator optional validation received link-layer frames
   * @return Optional List[Byte] representing an un-parsed fragment
   */
  def read(
      timeoutMs: Long,
      validator: FrameValidator = FrameValidator.none,
      linkOverride: LinkOverride = LinkOverride.default): Option[List[Byte]] = {

    val deadline = clock.deadline(timeoutMs)

    def recurse(seq: Option[Byte], data: List[Byte]): Option[List[Byte]] = {

      def evaluate(bytes: List[Byte]): Option[List[Byte]] = bytes match {
        case x :: message =>
          val ctrl = Transport.function(x)
          reporter.receive(ctrl)
          seq match {
            case Some(num) => // previous data
              if (ctrl.fir) throw new Exception("Received new fir frame, before segment completed")
              else {
                val expected = Transport.next(num)
                if (ctrl.seq == expected) {
                  if (ctrl.fin) Some(data ++ message)
                  else {
                    recurse(Some(ctrl.seq), data ++ message)
                  }
                } else throw new Exception("Unexpected transport segment sequence: " + ctrl.seq)
              }
            case None =>
              if (ctrl.fir) {
                if (ctrl.fin) Some(message)
                else {
                  recurse(Some(ctrl.seq), message)
                }
              } else throw new Exception("Expected FIR segment but received " + ctrl)
          }
        case Nil =>
          throw new Exception("Received 0 length segment")
      }

      // nothing (more) arrived in time: silence, unless a fragment was under way
      def timedOut: Option[List[Byte]] =
        if (seq.isDefined) throw new Exception("Timed out partway through a fragment, after its FIR segment")
        else None

      val remaining = clock.msLeft(deadline)
      if (remaining > 0) {
        reader.read(validator, linkOverride)(remaining) match {
          case Some(x) => evaluate(x)
          case None => timedOut
        }
      } else timedOut
    }

    recurse(None, Nil)
  }

}
