// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api.{ OptionSource, TestStep, describe }
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.protocol.parsing.UInt48LE

object GenericSteps {

  def send(message: Message, options: LinkOverride): TestStep = new TestStep {

    private def baseDescription: String = {
      if (options.any) s"$message using link options: $options"
      else message.toString
    }

    def description: List[Comment] = Comment(baseDescription) ::
      message.headers.flatMap(h => Comment.levelOne(h.toString) :: h.contents.map(c => Comment.levelTwo(c)).toList)

    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.writeMessage(message, options)
      Nil
    }
  }

  def send(message: Message): TestStep = send(message, LinkOverride.default)

  // Reads the clock when the step runs; a time built with send() would be captured before any earlier steps run
  def writeCurrentTime(seq: Sequence, latencyMs: Int = 0): TestStep = new TestStep {

    def description: List[Comment] = List(s"Write the current time + $latencyMs ms w/ $seq")

    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.writeMessage(Requests.writeTime(seq, UInt48LE(System.currentTimeMillis() + latencyMs)))
      Nil
    }
  }

  def send(function: AppFunction, seq: Sequence, header: ObjectHeader): TestStep = {
    send(function, seq, header, LinkOverride.default)
  }

  def send(function: AppFunction, seq: Sequence, header: ObjectHeader, options: LinkOverride): TestStep = {
    send(Message.request(seq, function, List(header)), options)
  }

  def send(function: AppFunction, seq: Sequence, headers: List[ObjectHeader] = Nil): TestStep = {
    send(function, seq, headers, LinkOverride.default)
  }

  def send(function: AppFunction, seq: Sequence, headers: List[ObjectHeader], options: LinkOverride): TestStep = {
    send(Message.request(seq, function, headers), options)
  }

  def confirm(seq: Sequence, options: LinkOverride = LinkOverride.default): TestStep = new TestStep {
    def description: List[Comment] = {
      if (options.any) {
        List(s"Send an application layer confirmation with $seq and link options: $options")
      } else {
        List(s"Send an application layer confirmation with $seq")
      }
    }
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.writeMessage(Requests.confirm(seq, Unsolicited.No), options)
      Nil
    }
  }

  def readClass0(seq: Sequence, options: LinkOverride = LinkOverride.default): TestStep =
    send(Requests.readClass0(seq), options)

  // the document's report-by-exception requirement for a DUT whose class 0 response takes several link frames, which
  // this tool doesn't verify; `points` names the points the procedure is about, if any
  def reportByExceptionNotVerified(points: Option[String]): TestStep = describe(
    s"Not verified by this tool: a DUT ${points.fold("")(p => s"with $p ")}whose class 0 response spans several link " +
      "frames must also report by exception.")

  // no answer to a request that mustn't get one: no frame at all for as long as the DUT may take to answer (the
  // response timeout), not just the link timeout
  def expectNoAnswer(options: OptionSource): TestStep = new TestStep {
    private val timeoutMs = responseTimeoutMs(options)
    override def description: List[Comment] =
      List(s"Verify that no link layer frames are received within $timeoutMs ms")
    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readFrame(Some(timeoutMs)).foreach(frame =>
        throw new Exception("Expecting no answer, but received: " + frame))
      Nil
    }
  }

  def responseTimeoutMs(options: OptionSource): Int = options.get(OutstationOptions.responseTimeout)

  /// ----- reading solicited responses -----

  // the response to a read that empties the DUT of pending events: a full buffer can take several fragments, and
  // each fragment that carries data asks for confirmation
  def expectEventsEmptied(seq: Sequence): TestStep =
    expectAtLeastOneResponse(seq, Validations.verifyConfirmRequestedIfNotNull)

  def expectNullResponse(seq: Sequence): TestStep = expectSingleResponse(seq, Validations.isNull)

  def expectNullOrObjectUnknownResponse(seq: Sequence): TestStep =
    expectSingleResponse(seq, Validations.isNullOrErrorResponse(IIN.OBJECT_UNKNOWN))

  // one fragment of a response, perhaps the first of several: neither waits for the rest nor confirms it
  def readResponseFragment(seq: Sequence, validation: MessageValidation): TestStep = new TestStep {
    override def description: List[Comment] = s"Read one fragment of the response w/ $seq" :: validation.description

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readMessage(Unsolicited.No) match {
        case None => throw new Exception("Unable to read complete message within timeout")
        case Some(msg) =>
          TestDriver.checkSolicited(msg, seq)
          validation.validate(driver.reporter, msg)
      }
    }
  }

  def expectSingleResponse(seq: Sequence): TestStep = expectSingleResponse(seq, MessageValidation.none)

  def expectSingleResponse(seq: Sequence, validation: MessageValidation): TestStep =
    expectSingleResponse(seq, AutoConfirm.No, validation)

  def expectSingleResponse(seq: Sequence, autoConfirm: AutoConfirm): TestStep =
    expectSingleResponse(seq, autoConfirm, MessageValidation.none)

  def expectSingleResponse(seq: Sequence, autoConfirm: AutoConfirm, validation: MessageValidation): TestStep =
    new TestStep {

      override def description: List[Comment] =
        s"Read a single fragment response w/ $seq" :: validation.description ::: autoConfirm.description

      override def run(driver: TestDriver): List[DeferredCheck] = {
        driver.readAllResponses(seq, autoConfirm, AppFunction.response) match {
          case message :: Nil =>
            validation.validate(driver.reporter, message)
          case x =>
            throw new Exception(s"Expected a single message response, but received: ${x.size}")
        }
      }
    }

  def expectAtLeastOneResponse(seq: Sequence): TestStep = {
    expectAtLeastOneResponse(seq, FrameValidator.none, ResponseValidation.none)
  }

  def expectAtLeastOneResponse(seq: Sequence, validation: MessageValidation): TestStep = {
    expectAtLeastOneResponse(seq, FrameValidator.none, validation.toResponseValidation)
  }

  def expectAtLeastOneResponse(seq: Sequence, validation: ResponseValidation): TestStep = {
    expectAtLeastOneResponse(seq, FrameValidator.none, validation)
  }

  def expectAtLeastOneResponse(seq: Sequence, validation: FrameValidator): TestStep = {
    expectAtLeastOneResponse(seq, validation, ResponseValidation.none)
  }

  def expectAtLeastOneResponse(
      seq: Sequence,
      frameValidator: FrameValidator,
      responseValidator: ResponseValidation): TestStep = new TestStep {

    override def description: List[Comment] = {
      val start: Comment = s"Read any number of valid responses beginning w/ $seq and incrementing by 1 modulo 16"
      start :: frameValidator.description ::: responseValidator.description ::: AutoConfirm.Yes.description
    }

    override def run(driver: TestDriver): List[DeferredCheck] = {
      val responses = driver.readAllResponses(seq, AutoConfirm.Yes, AppFunction.response, frameValidator)

      // perform aggregate response validation
      responseValidator.validate(driver.reporter, responses)
    }
  }

  def expectNoResponse: TestStep = expectNoResponse(None)

  def expectNoResponseFor(timeoutMs: Int): TestStep = expectNoResponse(Some(timeoutMs))

  // a DUT would retransmit an unconfirmed response after its application confirm timeout; wait several of them
  def expectNoRetransmission(appConfirmTimeoutMs: Int): TestStep = expectNoResponse(Some(3 * appConfirmTimeoutMs))

  private def expectNoResponse(timeoutMs: Option[Int]): TestStep = new TestStep {

    override def description: List[Comment] =
      List(timeoutMs.fold("Expect no response.")(ms => s"Expect no response within $ms ms."))

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readMessage(Unsolicited.No, timeoutMs = timeoutMs) match {
        case Some(_) => throw new Exception("Unexpected message received.")
        case None => Nil
      }
    }
  }

  // ----- reading unsolicited responses -----
  def expectUnsolicitedResponse(autoConfirm: AutoConfirm): TestStep = {
    expectUnsolicitedResponse(autoConfirm, FrameValidator.none, MessageValidation.none)
  }

  def expectUnsolicitedResponse(autoConfirm: AutoConfirm, messageValidation: MessageValidation): TestStep = {
    expectUnsolicitedResponse(autoConfirm, FrameValidator.none, messageValidation)
  }

  def expectUnsolicitedResponse(
      autoConfirm: AutoConfirm,
      frameValidator: FrameValidator,
      messageValidation: MessageValidation): TestStep = {
    expectUnsolicitedResponse(autoConfirm, frameValidator, messageValidation, LinkOverride.default)
  }

  def expectUnsolicitedResponse(
      autoConfirm: AutoConfirm,
      frameValidator: FrameValidator,
      messageValidation: MessageValidation,
      linkOverride: LinkOverride,
      timeoutMs: Option[Int] = None,
      noResponse: String = "No unsolicited response received."): TestStep = new TestStep {
    override def description: List[Comment] = "Read an unsolicited response" :: frameValidator.description :::
      messageValidation.description ::: autoConfirm.description

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readMessage(Unsolicited.Yes, frameValidator, linkOverride, timeoutMs) match {
        case None => throw new Exception(noResponse)
        case Some(msg) =>
          if (msg.ctrl.con.value) autoConfirm.execute(msg, driver)

          validateUnsolicitedResponse(msg)
          messageValidation.validate(driver.reporter, msg)
      }
    }
  }

  def confirmUnsolicitedNullResponse: TestStep = new TestStep {
    override def description: List[Comment] = List("If an unsolicited NULL response is received, confirm it")

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readMessage(Unsolicited.Yes, FrameValidator.none) match {
        case Some(msg) =>
          validateUnsolicitedResponse(msg)
          Validations.isNull.validate(driver.reporter, msg)
          driver.writeMessage(Requests.confirm(msg.ctrl.seq, Unsolicited.Yes), LinkOverride.default)
        case None =>
      }
      Nil
    }
  }

  def expectNoUnsolicitedResponse: TestStep = expectNoUnsolicitedResponse(None)

  def expectNoUnsolicitedResponse(timeoutMs: Option[Int]): TestStep = new TestStep {

    override def description: List[Comment] = timeoutMs match {
      case None => List("Expect no unsolicited response.")
      case Some(ms) => List(s"Expect no unsolicited response within $ms ms.")
    }

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readMessage(Unsolicited.Yes, FrameValidator.none, LinkOverride.default, timeoutMs) match {
        case Some(_) => throw new Exception("Unexpected message received.")
        case None =>
      }
      Nil
    }
  }

  private[conformance] def validateUnsolicitedResponse(msg: Message): Unit = {
    if (!msg.ctrl.uns.value) throw new Exception("Unexpected solicited response")
    if (msg.function != AppFunction.unsolicited) throw new Exception("Unexpected function code: " + msg.function)
    if (!msg.ctrl.con.value) throw new Exception("Unsolicited responses should always have the CON bit set")
    if (!msg.ctrl.fir.value || !msg.ctrl.fin.value)
      throw new Exception("Unsolicited responses must fit in single fragment")
  }
}
