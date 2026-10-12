// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s89

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.conformance.s8.helpers.UnconfirmedResponse
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// 8.9.2.2 steps 13-15: the fragment that steps 16-20 hold unconfirmed must have more to follow
@RunWith(classOf[JUnitRunner])
class SecondFragmentTest extends AnyFunSuite with Matchers {

  private def fragment(seq: Int, fir: Boolean, fin: Boolean, con: Boolean = true, uns: Boolean = false): Message =
    Message(
      Control(First(fir), Final(fin), Confirm(con), Unsolicited(uns), Sequence(seq.toByte)),
      AppFunction.response,
      Some(IIN.empty),
      Nil)

  private class Plugin extends DefaultIntegrationPlugin {
    var generated = 0
    override def generateMultiFragmentResponse(reporter: PluginReporter): Unit = generated += 1
  }

  private class Dut(plugin: Plugin, fragments: Message*) extends StubDriver(StubDriver.reporter(plugin)) {
    private val pending = mutable.Queue(fragments*)
    val written = mutable.ListBuffer.empty[(AppFunction, Int)]
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = if (pending.isEmpty) None else Some(pending.dequeue())
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      written += ((message.function, message.ctrl.seq.value.toInt))
  }

  // runs the step, then confirms the fragment it holds, to see which one that is
  private def run(dut: Dut): List[(AppFunction, Int)] = {
    val pending = new UnconfirmedResponse(5000)
    Procedure8922.readSecondFragment(pending).run(dut)
    pending.confirm.run(dut)
    dut.written.toList
  }

  test("holds a second fragment that has more to follow") {
    val plugin = new Plugin
    run(new Dut(plugin, fragment(2, fir = false, fin = false))) should equal(List((AppFunction.confirm, 2)))
    plugin.generated should equal(0)
  }

  test("if the second fragment is the last, confirms it and requests a multi-fragment response again") {
    val plugin = new Plugin
    val written = run(new Dut(plugin, fragment(2, fir = false, fin = true), fragment(3, fir = true, fin = false)))
    written should equal(List((AppFunction.confirm, 2), (AppFunction.read, 3), (AppFunction.confirm, 3)))
    plugin.generated should equal(1)
  }

  test("rejects a second fragment with UNS set, or that doesn't ask to be confirmed") {
    an[Exception] should be thrownBy run(new Dut(new Plugin, fragment(2, fir = false, fin = false, uns = true)))
    an[Exception] should be thrownBy run(new Dut(new Plugin, fragment(2, fir = false, fin = false, con = false)))
  }

  test("rejects a second fragment with FIR set, or a new response that isn't the first of several") {
    an[Exception] should be thrownBy run(new Dut(new Plugin, fragment(2, fir = true, fin = false)))
    an[Exception] should be thrownBy run(
      new Dut(new Plugin, fragment(2, fir = false, fin = true), fragment(3, fir = true, fin = true)))
  }
}
