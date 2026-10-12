// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s856

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.ClassOptions
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group60Var2, Group60Var3, Group60Var4 }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// 8.5.6.2 for a DUT that supports some of the event classes
@RunWith(classOf[JUnitRunner])
class Procedure8562Test extends AnyFunSuite with Matchers {

  // the plugin's events are checked by class
  private class Plugin extends DefaultIntegrationPlugin {
    val checked = mutable.ListBuffer.empty[EventClass]
    override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit = ()
    override def verifyClassEvents(
        reporter: CheckReporter,
        eventClass: EventClass,
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
      if (expectation == EventExpectation.LatestBatch) checked += eventClass
      None
    }
  }

  // answers each READ with a Null Response, or with an event (asking for a confirm if con) when answerWithEvent
  // says so for the request (its sequence number and objects); records the reads and the confirms
  private class Dut(
      answerWithEvent: (Int, List[Variation]) => Boolean,
      con: Boolean = true,
      val plugin: Plugin = new Plugin)
      extends StubDriver(StubDriver.reporter(plugin)) {
    val reads = mutable.ListBuffer.empty[List[Variation]]
    val requests = mutable.ListBuffer.empty[(AppFunction, Int)]
    private val pending = mutable.Queue.empty[Message]
    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value.toInt
      requests += ((message.function, seq))
      if (message.function == AppFunction.read) {
        val variations = message.headers.map(_.variation)
        reads += variations
        val event = answerWithEvent(seq, variations)
        val control = "%02X".format(0xC0 | (if (event && con) 0x20 else 0) | seq)
        val objects = if (event) " 02 01 17 01 05 81" else ""
        pending.enqueue(Message.parse(Hex.from(s"$control 81 00 00$objects")))
      }
    }
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = if (pending.isEmpty) None else Some(pending.dequeue())
  }

  private val noClasses = Options.from(
    List(ClassOptions.supportClass1, ClassOptions.supportClass2, ClassOptions.supportClass3).map(_.id -> "false").toMap)

  private def run(dut: Dut): Unit = Procedure8562.configure(noClasses).steps.foreach(_.step.run(dut))

  test("reads each class it doesn't support, expecting Null Responses") {
    val dut = new Dut((_, _) => false)
    run(dut)
    dut.reads.toList should equal(List(
      List(Group60Var2, Group60Var3, Group60Var4),
      List(Group60Var2),
      List(Group60Var3),
      List(Group60Var4),
      List(Group60Var2, Group60Var3, Group60Var4)))
  }

  test("an event in the response for a class the DUT doesn't support fails") {
    an[Exception] should be thrownBy run(new Dut((_, variations) => variations == List(Group60Var3)))
  }

  private val onlyClass2 = Options.from(
    List(ClassOptions.supportClass1, ClassOptions.supportClass3).map(_.id -> "false").toMap)

  // class 2's first read gets its event, the second a Null Response
  private def class2Events(seq: Int, variations: List[Variation]): Boolean = variations == List(Group60Var3) && seq == 2

  test("reads a supported class's events, confirms them, then reads again for a Null Response") {
    val dut = new Dut(class2Events)
    Procedure8562.configure(onlyClass2).steps.foreach(_.step.run(dut))
    dut.requests.toList should equal(List(
      (AppFunction.read, 0),
      (AppFunction.read, 1),
      (AppFunction.read, 2),
      (AppFunction.confirm, 2),
      (AppFunction.read, 3),
      (AppFunction.read, 4),
      (AppFunction.read, 5)
    ))
    dut.reads.toList.slice(2, 4) should equal(List(List(Group60Var3), List(Group60Var3)))
    dut.plugin.checked.toList should equal(List(EventClass.Class2))
  }

  test("a supported class's events must ask to be confirmed") {
    an[Exception] should be thrownBy
      Procedure8562
        .configure(onlyClass2)
        .steps
        .foreach(_.step.run(new Dut(class2Events, con = false)))
  }
}
