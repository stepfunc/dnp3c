// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, OutstationOptions, SerialOptions }
import io.stepfunc.dnp3c.conformance.common.GenericSteps.{ expectAtLeastOneResponse, send }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ Message, Sequence }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group60Var2, Group60Var3, Group60Var4 }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

/**
 * 3.1r1's 8.10.2, Multi-Drop Support, over serial: class 1, 2 and 3 reads to the other devices on the drop
 * (serial.otherDevices) and to the broadcast address, which nothing from the DUT may follow, and one to the DUT, which
 * it answers; then the same for each RTS delay setting (serial.rtsDelays). The harness sees what the DUT sends; whether
 * it raised carrier or asserted RTS, and when, is for the plugin to check on the line, e.g. with an oscilloscope.
 */
object MultiDrop {

  private val pairs = """\s*(\d{1,6})\s*/\s*(\d{1,6})\s*""".r

  /** the RTS delays to test, distinct, or why they can't be read */
  def rtsDelays(options: OptionSource): Either[String, List[RtsDelays]] = {
    val text = options.get(SerialOptions.rtsDelays).trim
    if (text.isEmpty) Right(Nil)
    else {
      val parsed = text.split(",", -1).toList.map {
        case pairs(before, after) => Some(RtsDelays(before.toInt, after.toInt))
        case _ => None
      }
      if (!parsed.forall(_.isDefined)) Left("must be before/after pairs in ms, separated by commas, e.g. 0/0, 10/5")
      else if (parsed.distinct.size < parsed.size) Left("lists a setting more than once")
      else Right(parsed.flatten)
    }
  }

  // the addresses of ordinary devices, as the link addresses allow: not broadcast, self or reserved ones
  private val maxAddress = 0xFFEF

  // what 8.10.2 needs configured: two or more other devices, and RTS delays that can be read
  private def problems(options: OptionSource): List[ConfigProblem] = {
    val others = options.get(SerialOptions.otherDevices).map(_.value)
    val own = Set(options.get(LinkOptions.destination), options.get(LinkOptions.source))
    List(
      Option.when(others.distinct.size < 2)(
        ConfigProblem(
          SerialOptions.otherDevices.id,
          others.mkString(", "),
          "3.1r1's 8.10.2 needs the addresses of at least two other devices on the drop")),
      Option.when(others.exists(_ > maxAddress))(
        ConfigProblem(
          SerialOptions.otherDevices.id,
          others.mkString(", "),
          s"must be ordinary devices' addresses, at most $maxAddress: not broadcast, self or reserved ones"
        )),
      Option.when(others.exists(own.contains))(
        ConfigProblem(
          SerialOptions.otherDevices.id,
          others.mkString(", "),
          s"mustn't hold the DUT's address (${LinkOptions.destination.id}) or the master's (${LinkOptions.source.id})"
        )),
      rtsDelays(options).left.toOption.map(why =>
        ConfigProblem(SerialOptions.rtsDelays.id, options.get(SerialOptions.rtsDelays), why))
    ).flatten
  }

  private def read(seq: Sequence): Message = Requests.readAllObjects(seq, Group60Var2, Group60Var3, Group60Var4)

  // nothing from the DUT's address within the window; frames from other devices are noted
  private def nothingFromDut(windowMs: Int): TestStep = new TestStep {
    def description: List[Comment] =
      List(s"Verify that no frame comes from the DUT within $windowMs ms; frames from other devices are noted")
    def run(driver: TestDriver): List[DeferredCheck] = {
      val deadline = driver.clock.deadline(windowMs)
      var left = driver.clock.msLeft(deadline)
      while (left > 0) {
        driver.readFrame(Some(left.toInt)).foreach { frame =>
          if (frame.header.src == driver.remote) throw new Exception(s"The DUT sent a frame: $frame")
          driver.reporter.info(s"A frame from address ${frame.header.src.value}: $frame")
        }
        left = driver.clock.msLeft(deadline)
      }
      Nil
    }
  }

  // checked at once, while the line's recording is of this exchange
  private val noCarrier: TestStep = new TestStep {
    def description: List[Comment] = List("Verify on the line that the DUT didn't raise carrier or assert RTS")
    def run(driver: TestDriver): List[DeferredCheck] =
      driver.reporter.plugin.verifyNoCarrier(driver.reporter.checkReporter(CheckTiming.Immediate)).toList
  }

  // the other devices' late answers, read until the line is quiet for the link timeout, so none mixes with the
  // DUT's response; a frame from the DUT fails
  private val quiet: TestStep = TestStep(List("Wait until the line is quiet, with nothing from the DUT")) { driver =>
    var frame = driver.readFrame()
    while (frame.isDefined) {
      frame.foreach { f =>
        if (f.header.src == driver.remote) throw new Exception(s"The DUT sent a frame: $f")
        driver.reporter.info(s"A frame from address ${f.header.src.value}: $f")
      }
      frame = driver.readFrame()
    }
  }

  private def carrierTiming(delays: Option[RtsDelays]): TestStep = new TestStep {
    def description: List[Comment] = List(
      "Verify on the line that the DUT raised carrier (RTS) an appropriate time before its response, and dropped it " +
        "an appropriate time after" + delays.fold("")(d => s", as set: ${d.beforeMs} ms and ${d.afterMs} ms"))
    def run(driver: TestDriver): List[DeferredCheck] =
      driver.reporter.plugin
        .verifyCarrierTiming(driver.reporter.checkReporter(CheckTiming.Immediate), delays)
        .toList
  }

  private def setDelays(delays: Option[RtsDelays]): TestStep =
    PluginSteps.action(delays.fold("Restore the DUT's usual RTS delays")(d =>
      s"Set the DUT to raise carrier (RTS) ${d.beforeMs} ms before transmitting, and to drop it ${d.afterMs} ms after")) {
      (plugin, reporter) => plugin.setRtsDelays(reporter, delays)
    }

  val multiDrop: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = List(SerialOptions.enabled, SerialOptions.multiDrop)

    override def configurationProblems(options: OptionSource): List[ConfigProblem] = problems(options)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val others = options.get(SerialOptions.otherDevices).map(_.value)
      val window = options.get(OutstationOptions.responseTimeout)
      val delays = rtsDelays(options).getOrElse(Nil)
      // a sequence number for each request, in order
      val seqs = Iterator.iterate(Sequence.zero)(_.next)
      def answered(seq: Sequence, d: Option[RtsDelays]) =
        quiet + send(read(seq)) + expectAtLeastOneResponse(seq) + carrierTiming(d)
      List(
        1 --> describe("The DUT supports a multi-drop physical layer"),
        2 --> describe(s"The DUT shares the drop with the devices at addresses ${others.mkString(", ")}"),
        3 --> describe("An oscilloscope or protocol analyzer watches the DUT's signals, for the plugin's checks"),
        (4 to 5) -->
          (if (others.isEmpty) describe("No other devices are set (serial.otherDevices)")
           else
             others
               .map(a =>
                 send(read(seqs.next()), LinkOverride.destination(UInt16LE(a))) + nothingFromDut(window) + noCarrier)
               .reduce(_ + _)),
        (6 to 7) -->
          (send(read(seqs.next()), LinkOverride.destination(UInt16LE(0xFFFF))) + nothingFromDut(window) + noCarrier),
        (8 to 9) --> answered(seqs.next(), None),
        Label("step: 9c") -->
          (if (delays.isEmpty) describe("The DUT's RTS delays aren't configurable (serial.rtsDelays)")
           else
             describe(
               "The settings (serial.rtsDelays) are to cover, for each delay, every selection where it has four or " +
                 "fewer, and otherwise its minimum, maximum and two values between") +
               delays.map(d => setDelays(Some(d)) + answered(seqs.next(), Some(d))).reduce(_ + _))
      )
    }

    override protected def cleanup(options: OptionSource): Iterable[LabeledStep] =
      if (rtsDelays(options).getOrElse(Nil).isEmpty) Nil
      else List(Label("cleanup") --> setDelays(None))
  }
}
