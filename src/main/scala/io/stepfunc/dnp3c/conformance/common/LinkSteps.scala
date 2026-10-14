// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.api.TestStep
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.link._
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.UInt8

object LinkSteps {

  /// ---- DSL for link layer testing ----

  val expectLinkTimeout: TestStep = expectLinkTimeout(None)

  def expectLinkTimeout(timeout: Option[Int]): TestStep = new TestStep {
    def description: List[Comment] = timeout match {
      case None => List("Verify that no link layer frames are received")
      case Some(x) => List(s"Verify that no link layer frames are received within $x ms")
    }
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readFrame(timeout).foreach(frame => throw new Exception("Expecting timeout, but received frame: " + frame))
      Nil
    }
  }

  // A frame that answers a link-layer request (an ACK or a link status), or retries a frame, comes within the link
  // timeout, the default. A frame the DUT sends to answer an application request, or the RESET LINK it sends first, can
  // take as long as the response timeout.
  def expectFrame(validator: FrameValidator, timeoutMs: Option[Int] = None): TestStep = new TestStep {

    override def description: List[Comment] = {
      val within = timeoutMs.map(ms => s" within $ms ms").getOrElse("")
      s"Verify that the DUT answers$within with a well-formed link frame" :: validator.description
    }

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readFrame(timeoutMs) match {
        case Some(frame) => validator.validate(frame)
        case None => throw new Exception("Timeout waiting for a link frame")
      }
      Nil
    }
  }

  // A retry within half the link timeout of the frame it repeats is too early: the DUT didn't wait for its link timeout.
  // Half leaves room for the DUT's timer and the transport on either side.
  def expectNoEarlyRetry(timeoutMs: Int): TestStep = new TestStep {
    def description: List[Comment] =
      List(s"Verify that the DUT doesn't retransmit within half the link timeout (${timeoutMs / 2} ms)")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readFrame(Some(timeoutMs / 2)).foreach(frame => throw new Exception("Retry sent too early: " + frame))
      Nil
    }
  }

  // A retry is accepted from half a link timeout to one and a half link timeouts after the frame it repeats: no frame
  // within that whole window means no more retries
  def expectNoMoreRetries(timeoutMs: Int): TestStep = new TestStep {
    def description: List[Comment] =
      List(
        s"Verify that the DUT sends no further retries within one and a half link timeouts (${timeoutMs * 3 / 2} ms)")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readFrame(Some(timeoutMs * 3 / 2)).foreach(frame => throw new Exception("Unexpected retry: " + frame))
      Nil
    }
  }

  // Read a frame within `firstWithinMs`, then verify that the DUT retransmits exactly that frame `retries` times, each a
  // link timeout after the previous one
  def expectFrameAndRetries(validator: FrameValidator, retries: Int, timeoutMs: Int, firstWithinMs: Int): TestStep =
    new TestStep {

      override def description: List[Comment] =
        s"Verify that the DUT sends a link frame within $firstWithinMs ms and retransmits it $retries time(s)" ::
          validator.description

      override def run(driver: TestDriver): List[DeferredCheck] = {
        val original =
          driver.readFrame(Some(firstWithinMs)).getOrElse(throw new Exception("Timeout waiting for a link frame"))
        validator.validate(original)
        for (_ <- 1 to retries) {
          expectNoEarlyRetry(timeoutMs).run(driver)
          expectRepeatOf(original, None, driver)
        }
        Nil
      }
    }

  // A frame left unconfirmed while the steps in between send something else: a later step expects the DUT to
  // retransmit it
  final class UnconfirmedFrame {
    private var frame: Option[LinkFrame] = None

    // expect the frame, as expectFrame does, and remember it
    def expect(validator: FrameValidator, timeoutMs: Option[Int] = None): TestStep = expectFrame(
      validator && new FrameValidator {
        override def description: List[Comment] = Nil
        override def validate(received: LinkFrame): Unit = frame = Some(received)
      },
      timeoutMs)

    // expect the frame again, unchanged, within `timeoutMs` (by default, the link timeout)
    def expectRepeat(timeoutMs: Option[Int] = None): TestStep = new TestStep {
      override def description: List[Comment] = {
        val within = timeoutMs.map(ms => s" within $ms ms").getOrElse("")
        List(s"Verify that the DUT sends that frame again$within, unchanged")
      }

      override def run(driver: TestDriver): List[DeferredCheck] = {
        expectRepeatOf(frame.getOrElse(throw new Exception("No frame to expect a repeat of")), timeoutMs, driver)
        Nil
      }
    }

    // expect the DUT to retransmit the frame a link timeout later
    def expectRetry(timeoutMs: Int): TestStep = expectNoEarlyRetry(timeoutMs) + expectRepeat()
  }

  private def expectRepeatOf(original: LinkFrame, timeoutMs: Option[Int], driver: TestDriver): Unit = {
    val repeat =
      driver.readFrame(timeoutMs).getOrElse(throw new Exception("Timeout waiting for a repeat of: " + original))
    if (repeat != original) throw new Exception(s"Expected a retransmission of $original, but received: $repeat")
  }

  def expectLinkTimeoutOr(validator: FrameValidator, timeoutMs: Int): TestStep = new TestStep {

    def description: List[Comment] =
      Comment(s"Expect no link layer frames within $timeoutMs ms, or a frame such that:") :: validator.description

    def run(driver: TestDriver): List[DeferredCheck] = {
      // a permitted frame doesn't end the observation: check every frame until the window ends
      val deadline = driver.clock.deadline(timeoutMs)
      var remaining = timeoutMs
      while (remaining > 0) {
        driver.readFrame(Some(remaining)).foreach(frame => validator.validate(frame))
        remaining = driver.clock.msLeft(deadline).toInt
      }
      Nil
    }
  }

  def sendLinkControl(control: Byte): TestStep = new TestStep {

    def description: List[Comment] = {
      List("Issue a " + getString(Control(control)) + " using link control block " + Hex.prefixed(control))
    }

    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.writeFrame(LinkFrame.headerOnly(Control(control), driver.remote, driver.local))
      Nil
    }
  }

  def sendLinkConfirm: TestStep = sendLinkControl(Control.ack.toByte)

  def expectLinkReset(timeoutMs: Int): TestStep = expectFrame(isLinkReset, Some(timeoutMs))

  // ------ Frame validations ------

  def withControl(expected: Byte): FrameValidator = withAnyControl(Set(expected))

  def withAnyControl(expected: Set[Byte]): FrameValidator = new FrameValidator {
    override def description: List[Comment] = {
      List("Verify that the frame uses link control block " + expected.map(x => Hex.prefixed(x)).mkString(" or "))
    }
    override def validate(frame: LinkFrame): Unit = {
      val actual = frame.header.ctrl.toByte
      if (!expected.contains(actual)) throw new Exception("Unexpected link control block: " + Hex.prefixed(actual))
    }
  }

  def withFunction(expected: LinkFunction): FrameValidator = new FrameValidator {
    override def description: List[Comment] = List(
      "Verify that the frame uses link function: " + expected.description
    )

    override def validate(frame: LinkFrame): Unit = {
      if (frame.header.ctrl.func != expected) {
        throw new Exception("Received unexpected function: " + frame.header.ctrl.func)
      }
    }
  }

  def isLinkReset: FrameValidator = withControl(Control(false, false, false, LinkFunction.RESET_LINK_STATES).toByte)

  def isNack: FrameValidator = withControl(Control(false, false, false, LinkFunction.NACK).toByte)

  def isConfirm: FrameValidator = withControl(Control(false, false, false, LinkFunction.ACK).toByte)

  def isConfirmedUserData: FrameValidator = {
    val control1 = Control(false, false, true, LinkFunction.CONFIRMED_USER_DATA).toByte
    val control2 = Control(false, true, true, LinkFunction.CONFIRMED_USER_DATA).toByte
    withAnyControl(Set(control1, control2))
  }

  // ------ private helpers ------

  private def getString(link: Control): String = link.func.description + " (" + Hex.prefixed(link.toByte) + ")"

}
