// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.FlagTestOptions
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group20Var0, Group60Var1, Group60Var2, Group60Var3, Group60Var4 }
import io.stepfunc.dnp3c.dnp3.link.Control
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

import scala.util.control.NonFatal

/**
 * 3.1r1's 8.6.5 tests: a broadcast freeze of the counters (g20v0, qualifier 0x06), and the all stations indication
 * (IIN1.0) it leaves in the next response, with or without a request for confirmation as the broadcast address asks.
 */
object Broadcast {

  private val allStations = UInt16LE(0xFFFF)
  private val confirmed = UInt16LE(0xFFFE)
  private val unconfirmed = UInt16LE(0xFFFD)

  private def freeze(seq: Sequence): Message = Requests.allObjects(AppFunction.freeze, seq, Group20Var0)

  // the requests of a test whose sequence numbers depend on what the outstation answers: each request takes the next
  private final class Sequencer {
    private var current = Sequence(15)
    def next(): Sequence = { current = current.next; current }
    def now: Sequence = current
  }

  // descriptions are built before the sequence numbers are known
  private def placeholder(comments: List[Comment], from: String, to: String): List[Comment] =
    comments.map(c => c.copy(value = c.value.replace(from, to)))

  private def sendNext(sequencer: Sequencer, request: Sequence => Message, link: LinkOverride = LinkOverride.default) =
    new TestStep {
      def description: List[Comment] =
        placeholder(send(request(Sequence.zero), link).description, "seq: 0x00", "seq: the next")
      def run(driver: TestDriver): List[DeferredCheck] = send(request(sequencer.next()), link).run(driver)
    }

  private def expectNow(sequencer: Sequencer, autoConfirm: AutoConfirm, validation: MessageValidation) = new TestStep {
    def description: List[Comment] = placeholder(
      expectSingleResponse(Sequence.zero, autoConfirm, validation).description,
      "sequence(0x00)",
      "the sequence number of the request before it")
    def run(driver: TestDriver): List[DeferredCheck] =
      expectSingleResponse(sequencer.now, autoConfirm, validation).run(driver)
  }

  private def readClass1(seq: Sequence): Message = Requests.readClass1(seq)

  /**
   * 8.6.5-1: the broadcast to 0xFFFF asks for a link layer confirmation. A DUT that doesn't act on it, as a broadcast
   * should only be sent as unconfirmed user data (TB2013-002), gets it again without one (step 5).
   */
  val allStationsIndication: ProcedureDefinition = new ProcedureDefinition {

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val sequencer = new Sequencer
      val linkConfirmation =
        LinkOverride.destination(allStations).withControlBlock(Control.confirmedUserData(true).toByte)
      List(
        1 --> sendNext(sequencer, freeze, linkConfirmation),
        (2 to 3) --> expectNoAnswer(options),
        4 --> sendNext(sequencer, readClass1),
        (5 to 7) --> new TestStep {
          def description: List[Comment] = List(
            "Read the response, confirming it if it asks. If there's no valid response with IIN1.0 set, broadcast " +
              "the freeze to 0xFFFF again without a link layer confirmation, verify that nothing answers, and read " +
              "class 1 again (steps 5.1 to 5.4)",
            "Verify that the response is valid, with IIN1.0 set and no error IIN bits, and confirm it if it asks " +
              "(steps 6 and 7)"
          )
          def run(driver: TestDriver): List[DeferredCheck] = {
            def broadcastSet(responses: List[Message]): Boolean =
              responses.headOption.flatMap(_.iin).exists(iin => (iin & IIN.BROADCAST) == IIN.BROADCAST)
            // anything short of a valid response with IIN1.0 calls for the second broadcast; a response that breaks
            // one of 3.1r1's rules is a failure in itself
            val first =
              try Some(driver.readAllResponses(sequencer.now, AutoConfirm.Yes, AppFunction.response))
              catch {
                case ex: PolicyViolation => throw ex
                case NonFatal(ex) =>
                  driver.reporter.info(s"No valid response to step 4: ${ex.getMessage}")
                  None
              }
            val responses =
              first.filter(broadcastSet) match {
                case Some(valid) => valid
                case None =>
                  driver.reporter.info(
                    "No valid response with IIN1.0: broadcasting the freeze again, as unconfirmed user data (steps " +
                      "5.1 to 5.4)")
                  send(freeze(sequencer.next()), LinkOverride.destination(allStations)).run(driver)
                  expectNoAnswer(options).run(driver)
                  send(readClass1(sequencer.next())).run(driver)
                  driver.readAllResponses(sequencer.now, AutoConfirm.Yes, AppFunction.response)
              }
            if (!broadcastSet(responses)) throw new Exception("Expected IIN1.0 (all stations) set in the response")
            responses.foreach(without(IIN.errors).validate(driver.reporter, _))
            Nil
          }
        },
        8 --> sendNext(sequencer, readClass1),
        9 --> expectNow(sequencer, AutoConfirm.No, isNull && without(IIN.BROADCAST))
      )
    }
  }

  /**
   * 8.6.5-2: broadcasts as unconfirmed user data, to 0xFFFE, which has the DUT ask for its response with IIN1.0 to be
   * confirmed and keep IIN1.0 until it is, and to 0xFFFD, which has it not ask
   */
  val confirmedResponseOptions: ProcedureDefinition = new ProcedureDefinition {

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val sequencer = new Sequencer
      val readClass012 = (seq: Sequence) => Requests.readAllObjects(seq, Group60Var1, Group60Var2, Group60Var3)
      val eventClass = options.get(FlagTestOptions.broadcastReadClass) match {
        case 1 => Group60Var2
        case 2 => Group60Var3
        case _ => Group60Var4
      }
      val readEvents = (seq: Sequence) => Requests.readAllObjects(seq, eventClass)
      List(
        1 --> sendNext(sequencer, readClass012),
        2 --> new TestStep {
          def description: List[Comment] = List(
            "While the response carries data and asks for confirmation, confirm it and repeat step 1, so that no " +
              "events are left")
          def run(driver: TestDriver): List[DeferredCheck] = {
            // the procedure sets no limit; this one only keeps a DUT that never stops from hanging the run
            val limit = 1000
            def drained(n: Int): Unit = {
              val responses = driver.readAllResponses(sequencer.now, AutoConfirm.Yes, AppFunction.response)
              if (responses.exists(m => m.headers.nonEmpty && m.ctrl.con.value)) {
                if (n >= limit) throw new Exception(s"The DUT still asks for confirmation of data after $limit reads")
                send(readClass012(sequencer.next())).run(driver)
                drained(n + 1)
              }
            }
            drained(1)
            Nil
          }
        },
        3 --> sendNext(sequencer, freeze, LinkOverride.destination(confirmed)),
        (4 to 5) --> expectNoAnswer(options),
        6 --> sendNext(sequencer, readClass1),
        7 --> expectNow(sequencer, AutoConfirm.No, isNull && containing(IIN.BROADCAST) && verifyConfirmRequested),
        8 -->
          (sendNext(sequencer, readClass1) +
            expectNow(sequencer, AutoConfirm.No, containing(IIN.BROADCAST) && verifyConfirmRequested)),
        9 --> new TestStep {
          def description: List[Comment] = List("Send the application layer confirmation the response asked for")
          def run(driver: TestDriver): List[DeferredCheck] =
            send(Requests.confirm(sequencer.now, Unsolicited.No)).run(driver)
        },
        10 --> sendNext(sequencer, readClass1),
        // the document's step 11 says IIN1.1 where it means IIN1.0, the bit steps 7 and 8 check
        11 --> expectNow(sequencer, AutoConfirm.No, isNull && without(IIN.BROADCAST)),
        12 --> sendNext(sequencer, freeze, LinkOverride.destination(unconfirmed)),
        (13 to 14) --> expectNoAnswer(options),
        15 --> sendNext(sequencer, readEvents),
        16 --> expectNow(sequencer, AutoConfirm.No, isNull && containing(IIN.BROADCAST) && verifyConfirmNotRequested)
      )
    }
  }
}
