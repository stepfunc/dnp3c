// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Message, Sequence, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame, LinkFunction }
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

/**
 * 3.1r1's 6.6.2-4, No Inter-frame Gap (Section 2, Note 18): one transmission carries an application layer confirm to
 * another address and, with no gap, a READ to the DUT, which must answer the READ as it would on its own. dnp3c reads
 * class 0 alone first, and compares the two responses' objects: their groups, variations, qualifiers and indexes, as
 * the values may change between them.
 *
 * The test is for serial and UDP, and for TCP only where the DUT has more than one DNP3 address. dnp3c connects over
 * TCP only, so it runs the test for such a DUT, sending the confirm to its other address.
 */
object NoInterFrameGap {

  // an unconfirmed user data frame from the master, carrying the message in one transport segment
  private def frame(driver: TestDriver, destination: Int, message: Message, transportSeq: Int): List[Byte] =
    LinkFrame
      .withData(
        Control(master = true, fcb = false, fcv = false, LinkFunction.UNCONFIRMED_USER_DATA),
        UInt16LE(destination),
        driver.local,
        Transport.function(fir = true, fin = true, transportSeq.toByte) :: message.toBytes
      )
      .toBytes

  // IIN bits that may change between two reads: new events, a broadcast, an event buffer that overflows
  private val changing =
    IIN.BROADCAST | IIN.CLASS1_EVENTS | IIN.CLASS2_EVENTS | IIN.CLASS3_EVENTS | IIN.EVENT_BUFFER_OVERFLOW

  // what the responses report, but for the values: each point's variation, qualifier and index, however headers and
  // fragments divide them, and the IIN bits that shouldn't change
  private def shape(responses: List[Message]): (List[(String, String, String)], IIN) = (
    responses.flatMap(_.headers).flatMap(h =>
      h.iterate.map(p => (h.variation.toString, h.qualifier.toString, p.idx.toString))),
    responses.foldLeft(IIN.empty)((all, m) => all | (m.iin.getOrElse(IIN.empty) & ~changing)))

  val noInterFrameGap: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = List(SupportOptions.multipleAddresses)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val other = options.get(LinkOptions.otherDestination)
      if (other == options.get(LinkOptions.destination))
        throw new Exception(
          s"${LinkOptions.otherDestination.id} is the DUT's own address, ${LinkOptions.destination.id}")
      var reference: Option[(List[(String, String, String)], IIN)] = None

      val record = new ResponseValidation {
        def description: List[Comment] = List("Record the objects the response reports")
        def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
          reference = Some(shape(responses))
          Nil
        }
      }

      val sameAsAlone = new ResponseValidation {
        def description: List[Comment] = List(
          "Verify that the response reports the same points, by variation, qualifier and index, and the same IIN " +
            "bits but those that may change between reads (IIN1.0 to IIN1.3, IIN2.3), as the response to the READ " +
            "on its own")
        def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
          val expected = reference.getOrElse(throw new Exception("The READ on its own wasn't answered"))
          if (shape(responses) != expected)
            throw new Exception("The DUT answered the READ differently after the confirm to another address")
          Nil
        }
      }

      val transmit = new TestStep {
        def description: List[Comment] = List(
          s"In one transmission, with no gap: an application layer confirm to address $other, then a READ of class 0 " +
            s"to the DUT w/ ${Sequence.one}")
        def run(driver: TestDriver): List[DeferredCheck] = {
          driver.write(
            frame(driver, other, Requests.confirm(Sequence.zero, Unsolicited.No), 0) ++
              frame(driver, driver.remote.value, Requests.readClass0(Sequence.one), 1))
          Nil
        }
      }

      List(
        1 -->
          (describe("For reference, READ class 0 on its own") + send(Requests.readClass0(Sequence.zero)) +
            expectAtLeastOneResponse(Sequence.zero, record) + transmit),
        // and nothing more, e.g. from the other address
        2 --> (expectAtLeastOneResponse(Sequence.one, sameAsAlone) + expectNoAnswer(options)),
        3 --> describe("dnp3c connects over TCP only: it can't repeat the test over serial or UDP"),
        4 --> describe(s"Over TCP, for a DUT with more than one DNP3 address: the confirm goes to its other, $other")
      )
    }
  }
}
