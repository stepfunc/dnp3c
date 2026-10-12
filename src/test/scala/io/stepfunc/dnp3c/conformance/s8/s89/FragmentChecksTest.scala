// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s89

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class FragmentChecksTest extends AnyFunSuite with Matchers {

  private val reporter = StubDriver.reporter()

  // a response fragment with the control byte's FIR and FIN, and sequence 0
  private def fragment(fir: Boolean, fin: Boolean): Message =
    Message.parse(Hex.from("%02X 81 00 00".format((if (fir) 0x80 else 0) | (if (fin) 0x40 else 0))))

  test("the first of several fragments has FIR set and FIN clear") {
    def passes(m: Message) = scala.util.Try(Validations.isFirstOfSeveralFragments.validate(reporter, m)).isSuccess
    passes(fragment(fir = true, fin = false)) should equal(true)
    passes(fragment(fir = true, fin = true)) should equal(false)
    passes(fragment(fir = false, fin = false)) should equal(false)
  }

  test("a response in several fragments has more than one") {
    def passes(ms: Message*) = scala.util.Try(Validations.inSeveralFragments.validate(reporter, ms.toList)).isSuccess
    passes(fragment(fir = true, fin = false), fragment(fir = false, fin = true)) should equal(true)
    passes(fragment(fir = true, fin = true)) should equal(false)
  }

  private class Plugin extends DefaultIntegrationPlugin {
    override def cyclePower(reporter: PluginReporter): Unit = ()
    override def setMaxFragmentSize(reporter: PluginReporter, maxFragmentSize: Int): Unit = ()
    override def generateMultiFragmentResponse(reporter: PluginReporter): Unit = ()
  }

  // answers every READ with a response in one fragment
  private class Dut extends StubDriver(StubDriver.reporter(new Plugin)) {
    private val pending = mutable.Queue.empty[Message]
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      pending.enqueue(Message.parse(Hex.from("%02X 81 00 00".format(0xC0 | message.ctrl.seq.value))))
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = if (pending.isEmpty) None else Some(pending.dequeue())
    override def close(): Unit = ()
  }

  private def run(multiFragment: Boolean): Unit = {
    val dut = new Dut
    Procedure8912
      .configure(Options.from(Map(SupportOptions.multiFragmentResponses.id -> multiFragment.toString)))
      .steps
      .foreach(_.step.run(dut))
  }

  test("8.9.1.2 needs several fragments only when the data for them was set up") {
    noException should be thrownBy run(multiFragment = false)
    an[Exception] should be thrownBy run(multiFragment = true)
  }
}
