// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6

import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.{ BehaviorOptions, LinkOptions, OutstationOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s6.s61.s612.Procedure612
import io.stepfunc.dnp3c.conformance.s6.s64.Procedure642
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.s6.s66.s663.{ Procedure6631, Procedure6632, Procedure6633, Procedure6634 }
import io.stepfunc.dnp3c.dnp3.link.{ Control, FrameValidator, LinkFrame, LinkFunction, LinkHeader }
import io.stepfunc.dnp3c.dnp3.{ DefaultIntegrationPlugin, LinkOverride, PluginReporter, ProcedureReporter, StubDriver }
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Message, Sequence, Unsolicited }
import io.stepfunc.dnp3c.dnp3.app.{ Control => AppControl }
import io.stepfunc.dnp3c.protocol.parsing.UInt8
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class RetryTimingTest extends AnyFunSuite with Matchers {

  // returns the queued results of readFrame in order, then timeouts, and records the timeouts it was given
  private class Driver(reads: List[Option[LinkFrame]], reporter: ProcedureReporter = StubDriver.reporter())
      extends StubDriver(reporter) {
    private var pending = reads
    var timeouts = List.empty[Option[Int]]
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = {
      timeouts = timeouts :+ timeout
      pending match {
        case next :: rest =>
          pending = rest
          next.orElse(timedOut(timeout))
        case Nil => timedOut(timeout)
      }
    }
  }

  private def frame(fcb: Boolean, data: List[Byte] = List(1, 2, 3)): LinkFrame =
    LinkFrame.withData(Control(false, fcb, true, LinkFunction.CONFIRMED_USER_DATA), UInt16LE(1), UInt16LE(1024), data)

  // each retry is a timeout (nothing in the first half of the link timeout) followed by the retry
  private def passes(reads: Option[LinkFrame]*): Boolean =
    scala.util.Try(expectFrameAndRetries(isConfirmedUserData, 2, 1000, 3000).run(new Driver(reads.toList))).isSuccess

  test("Accepts exact retransmissions, each after half a link timeout without frames") {
    val driver = new Driver(List(Some(frame(true)), None, Some(frame(true)), None, Some(frame(true))))
    expectFrameAndRetries(isConfirmedUserData, 2, 1000, 3000).run(driver)
    // the first frame within the given time, each retry within a link timeout
    driver.timeouts should equal(List(Some(3000), Some(500), None, Some(500), None))
  }

  test("Rejects a retry with a toggled FCB") {
    passes(Some(frame(true)), None, Some(frame(false)), None, Some(frame(true))) should be(false)
  }

  test("Rejects a retry with different data") {
    passes(Some(frame(true)), None, Some(frame(true, List(9))), None, Some(frame(true))) should be(false)
  }

  test("Rejects a retry sent too early") {
    passes(Some(frame(true)), Some(frame(true)), Some(frame(true)), None, Some(frame(true))) should be(false)
  }

  test("Rejects a missing retry") {
    passes(Some(frame(true)), None, Some(frame(true)), None, None) should be(false)
  }

  private val reset = LinkFrame(
    LinkHeader(UInt8(5), Control(false, false, false, LinkFunction.RESET_LINK_STATES), UInt16LE(1), UInt16LE(1024)))

  // a DUT whose link frames arrive as scripted: the plugin, the requests and the link confirms do nothing
  private class Dut(reads: List[Option[LinkFrame]])
      extends Driver(
        reads,
        StubDriver.reporter(new DefaultIntegrationPlugin {
          override def cyclePower(reporter: PluginReporter): Unit = ()
          override def setLinkLayerConfirm(reporter: PluginReporter, value: Boolean): Unit = ()
        })
      ) {
    override def writeMessage(message: Message, options: LinkOverride): Unit = ()
    override def writeFrame(frame: LinkFrame): Unit = ()
    override def close(): Unit = ()
  }

  private def run642(reads: Option[LinkFrame]*): Dut = {
    val dut = new Dut(reads.toList)
    val options = Options.from(
      Map(
        LinkOptions.timeout.id -> "1000",
        LinkOptions.numRetry.id -> "2",
        OutstationOptions.responseTimeout.id -> "3000"))
    Procedure642.configure(options).steps.foreach(_.step.run(dut))
    dut
  }

  // a frame and two retries; None is a timeout
  private val data = Some(frame(true))
  private val resets = List(Some(reset), None, Some(reset), None, Some(reset))
  private val dataRetried = List(data, None, data, None, data)

  test("6.4.2 expects each retry after half a link timeout without frames, then no more") {
    val dut =
      run642(resets ::: List(None, Some(reset)) ::: dataRetried ::: List(None, Some(reset), data, None, data, None)*)
    // a frame within the response timeout, then each retry after half a link timeout without frames
    def retried(retries: Int) = Some(3000) :: List.fill(retries)(List(Some(500), None)).flatten
    // nothing for as long as another retry would be accepted
    val noMore = List(Some(1500))
    dut.timeouts should equal(
      // RESET LINK and two retries, then nothing
      retried(2) ::: noMore :::
        // the confirmed RESET LINK, then user data and two retries, then nothing
        List(Some(3000)) ::: retried(2) ::: noMore :::
        // the confirmed RESET LINK, then user data and a retry that's confirmed, then nothing
        List(Some(3000)) ::: retried(1) ::: noMore)
  }

  test("6.4.2 rejects a retry sent too early, and one retry too many") {
    an[Exception] should be thrownBy run642(Some(reset), Some(reset))
    an[Exception] should be thrownBy run642(resets ::: List(Some(reset))*)
  }

  test("6.4.2 lets the confirmed frame (FCB set) be followed by the response's next frame, but not by a retry") {
    val upToStep16 = resets ::: List(None, Some(reset)) ::: dataRetried ::: List(None, Some(reset))
    val confirmed = List(data, None, data)
    noException should be thrownBy run642(upToStep16 ::: confirmed ::: List(Some(frame(false)))*)
    an[Exception] should be thrownBy run642(upToStep16 ::: confirmed ::: List(data)*)
    val fcbClear = Some(frame(false))
    an[Exception] should be thrownBy run642(upToStep16 ::: List(fcbClear, None, fcbClear)*)
  }

  // A DUT for 6.1.2's steps 23-30: once link confirms are on, it answers a class 0 read with a RESET LINK, the ACK of
  // that with `first`, a NACK with another RESET LINK, and the ACK of that with `repeat`. Nothing else arrives.
  private class LinkConfirmPlugin extends DefaultIntegrationPlugin {
    var confirms = false
    override def cyclePower(reporter: PluginReporter): Unit = ()
    override def setLinkLayerConfirm(reporter: PluginReporter, value: Boolean): Unit = confirms = value
  }

  private class Dut612(plugin: LinkConfirmPlugin, first: LinkFrame, repeat: LinkFrame)
      extends StubDriver(StubDriver.reporter(plugin)) {
    private var answers = List.empty[LinkFrame]
    private val frames = scala.collection.mutable.Queue.empty[LinkFrame]
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      if (plugin.confirms) {
        frames.enqueue(reset)
        answers = List(first, repeat)
      }
    override def write(bytes: List[Byte]): Unit = ()
    override def writeFrame(frame: LinkFrame): Unit = frame.header.ctrl.func match {
      case LinkFunction.ACK if answers.nonEmpty =>
        frames.enqueue(answers.head)
        answers = answers.tail
      case LinkFunction.NACK => frames.enqueue(reset)
      case _ =>
    }
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = timedOut(timeoutMs)
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] =
      if (frames.isEmpty) timedOut(timeout) else Some(frames.dequeue())
    override def close(): Unit = ()
  }

  test("6.1.2 step 30 expects the class 0 response repeated, unchanged") {
    val settings = Map(
      SupportOptions.dataLinkConfirmRequests.id -> "true",
      BehaviorOptions.resetsLinkUponReceivingNack.id -> "true")
    // runs every step, carrying on past failures: this DUT fails steps 1 to 21 either way
    def failedSteps(repeat: LinkFrame): Int = {
      val dut = new Dut612(new LinkConfirmPlugin, frame(true), repeat)
      Procedure612.configure(Options.from(settings)).steps.count(s => scala.util.Try(s.step.run(dut)).isFailure)
    }
    failedSteps(frame(true, List(9))) should equal(failedSteps(frame(true)) + 1)
  }

  // A DUT for 6.6.3: answers the prologue's class 0 read (sequence 0) with a RESET LINK and then a response, the others
  // with `data`, and a malformed link confirm with `retry` a link timeout later
  private class Dut663(retry: LinkFrame)
      extends StubDriver(StubDriver.reporter(new DefaultIntegrationPlugin {
        override def cyclePower(reporter: PluginReporter): Unit = ()
        override def setLinkLayerConfirm(reporter: PluginReporter, value: Boolean): Unit = ()
      })) {
    private val frames = scala.collection.mutable.Queue.empty[Option[LinkFrame]]
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      frames.enqueue(Some(if (message.ctrl.seq.value == 0) reset else data.get))
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] =
      Some(Message(AppControl.request(Sequence.zero), AppFunction.response, Some(IIN.empty), Nil))
    override def write(bytes: List[Byte]): Unit = malformedConfirm()
    override def writeFrame(frame: LinkFrame): Unit =
      if (frame.header.dest != remote || frame.header.ctrl.func != LinkFunction.ACK) malformedConfirm()
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] =
      if (frames.isEmpty) timedOut(timeout) else frames.dequeue().orElse(timedOut(timeout))
    override def close(): Unit = ()
    private def malformedConfirm(): Unit = frames.enqueueAll(List(None, Some(retry)))
  }

  test("6.6.3 expects a retransmission of the frame, unchanged") {
    def passes(procedure: ProcedureDefinition, retry: LinkFrame) = {
      val dut = new Dut663(retry)
      scala.util.Try(procedure.configure(Options.from(Map())).steps.foreach(_.step.run(dut))).isSuccess
    }
    for (procedure <- List(Procedure6631, Procedure6632, Procedure6633, Procedure6634)) withClue(procedure.id) {
      passes(procedure, frame(true)) should be(true)
      passes(procedure, frame(false)) should be(false)
      passes(procedure, frame(true, List(9))) should be(false)
    }
  }

}
