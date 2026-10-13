// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8

import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.s6
import io.stepfunc.dnp3c.conformance.api.options.{
  BehaviorOptions,
  LinkOptions,
  OutstationOptions,
  SupportOptions,
  UnsolicitedOptions
}
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ Message, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link.{ Control, FrameValidator, LinkFrame, LinkHeader }
import io.stepfunc.dnp3c.protocol.parsing.UInt8
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.io.StringReader

import scala.collection.mutable
import scala.util.Try

// how long the steps that expect silence wait for it
@RunWith(classOf[JUnitRunner])
class SilenceWaitsTest extends AnyFunSuite with Matchers {

  // nothing ever arrives, or, with acks, a link ACK for each frame read; records how long each read waits
  private class Silent(acks: Boolean = false) extends StubDriver(StubDriver.reporter()) {
    val frameWaits = mutable.ListBuffer.empty[Option[Int]]
    val unsolicitedWaits = mutable.ListBuffer.empty[Option[Int]]
    override def write(bytes: List[Byte]): Unit = ()
    override def writeFrame(frame: LinkFrame): Unit = ()
    override def writeMessage(message: Message, options: LinkOverride): Unit = ()
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = {
      frameWaits += timeout
      if (acks) Some(LinkFrame(LinkHeader(UInt8(5), Control(0x00.toByte), local, remote))) else timedOut(timeout)
    }
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = {
      if (unsolicited.value) unsolicitedWaits += timeoutMs
      timedOut(timeoutMs)
    }
    override def close(): Unit = ()
  }

  // runs every step, carrying on past failures; a manual question gets no answer (end of input), so it fails rather
  // than waits
  private def runEachStep(
      procedure: ProcedureDefinition,
      settings: Map[String, String],
      driver: Silent = new Silent): Silent = {
    Console.withIn(new StringReader(""))(
      procedure.configure(Options.from(settings)).steps.foreach(s => Try(s.step.run(driver))))
    driver
  }

  test("the broadcast procedures wait the configured response timeout for no answer") {
    val settings = Map(OutstationOptions.responseTimeout.id -> "3000")
    // 8.6.5.4 runs twice, once for each broadcast address
    for ((
        procedure,
        waits) <- List(s86.s865.Procedure8652 -> 1, s86.s865.Procedure8653 -> 1, s86.s865.Procedure8654 -> 2))
      withClue(procedure.id)(runEachStep(procedure, settings).frameWaits.count(_.contains(3000)) should equal(waits))
  }

  test("a request that mustn't be answered waits the configured response timeout for no answer") {
    // without link confirms, 6.1.2 stops at step 22: its later steps wait the response timeout for an answer
    val settings = Map(
      OutstationOptions.responseTimeout.id -> "2500",
      SupportOptions.selfAddress.id -> "true",
      SupportOptions.dataLinkConfirmRequests.id -> "false")
    for ((procedure, waits) <- List(
        // 6.1.2 steps 5 and 7 (steps 17 and 21 first expect a link confirm, which this DUT doesn't send)
        s6.s61.s612.Procedure612 -> 2,
        // step 6, after each of the two invalid function codes
        s6.s66.s662.Procedure6622 -> 2,
        s6.s67.Procedure672 -> 1,
        s82.s823.Procedure82321 -> 1,
        s82.s823.Procedure82322 -> 1,
        s82.s823.Procedure82323 -> 1,
        s84.s843.Procedure84321 -> 1,
        s84.s843.Procedure84322 -> 1,
        s84.s843.Procedure84323 -> 1
      ))
      withClue(procedure.id)(runEachStep(procedure, settings).frameWaits.count(_.contains(2500)) should equal(waits))

    // 6.1.2 steps 17 and 21 wait after the DUT's link confirm, as steps 5 and 7 wait for no answer at all
    val acked = runEachStep(s6.s61.s612.Procedure612, settings, new Silent(acks = true))
    acked.frameWaits.count(_.contains(2500)) should equal(4)
  }

  test("the first link frame a request causes is awaited for the response timeout, not the link timeout") {
    val settings = Map(
      OutstationOptions.responseTimeout.id -> "2500",
      LinkOptions.timeout.id -> "100",
      SupportOptions.dataLinkConfirmRequests.id -> "true",
      SupportOptions.dataLinkRetries.id -> "true",
      BehaviorOptions.resetsLinkUponReceivingNack.id -> "false"
    )
    // each read fails, which ends its step
    for ((procedure, waits) <- List(
        // steps 5 and 7, which expect no answer, then the RESET LINK and response at steps 26 and 28, twice (step 31)
        s6.s61.s612.Procedure612 -> 6,
        // the RESET LINK at steps 5, 9 and 16, and the user data at steps 10 and 17
        s6.s64.Procedure642 -> 5,
        // the RESET LINK at step 10 and the user data at step 12
        s6.s65.Procedure652 -> 2,
        // the prologue's RESET LINK, then the user data at steps 2 and 6; all twice
        s6.s66.s663.Procedure6631 -> 6,
        // the prologue's RESET LINK, then the user data; twice
        s6.s66.s663.Procedure6632 -> 4,
        s6.s66.s663.Procedure6633 -> 4,
        s6.s66.s663.Procedure6634 -> 4
      ))
      withClue(procedure.id)(runEachStep(procedure, settings).frameWaits.count(_.contains(2500)) should equal(waits))
  }

  test("after new events, no unsolicited response is expected for as long as the DUT could take to send one") {
    val settings =
      Map(UnsolicitedOptions.holdOffMs.id -> "500", UnsolicitedOptions.unsolicitedConfirmTimeoutMs.id -> "2000")
    def eventWaits(procedure: ProcedureDefinition) =
      runEachStep(procedure, settings).unsolicitedWaits.count(_.contains(2700))
    // 8.11.2.6: after the events, and after the request to enable unsolicited responses
    eventWaits(s811.Procedure81126) should equal(2)
    // 8.11.2.2: before unsolicited responses are enabled, when they are, and for the other classes' events
    eventWaits(s811.Procedure81122) should equal(3)
  }
}
