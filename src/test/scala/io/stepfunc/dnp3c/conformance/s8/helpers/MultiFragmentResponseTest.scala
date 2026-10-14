// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.s8.{ s814, s817, s819, s822, s83, s86, s87 }
import io.stepfunc.dnp3c.conformance.s8.s85.s855.Procedure85522
import io.stepfunc.dnp3c.conformance.s8.s85.s856.Procedure8562
import io.stepfunc.dnp3c.conformance.s8.s86.s866.Procedure86621
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.io.StringReader
import scala.collection.mutable
import scala.util.Try

// responses that take more than one fragment
@RunWith(classOf[JUnitRunner])
class MultiFragmentResponseTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit = ()
    override def verifyClassEvents(
        reporter: CheckReporter,
        eventClass: EventClass,
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = None
    override def verifyAllStaticCurrentValues(
        reporter: CheckReporter,
        isClass0: Boolean,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = None
    var tolerancesChecked = 0
    override def checkAnalogOutputTolerance(reporter: CheckReporter, expected: Double, actual: Double) = {
      tolerancesChecked += 1
      None
    }
  }

  // a fragment: whether it asks for confirmation, whether it's the last, and its objects
  private case class Fragment(con: Boolean, fin: Boolean, objects: String, iin: IIN = IIN.empty)

  // answers the request with this sequence number with the fragments, numbering them on from it, and any other request
  // with a null response
  private class Dut(seq: Int, fragments: List[Fragment], val plugin: Plugin = new Plugin)
      extends StubDriver(StubDriver.reporter(plugin)) {
    private val pending = mutable.Queue.empty[Message]

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val requestSeq = message.ctrl.seq.value.toInt
      if (message.function == AppFunction.confirm) ()
      else if (requestSeq != seq)
        pending.enqueue(Message(Control.request(message.ctrl.seq), AppFunction.response, Some(IIN.empty), Nil))
      else fragments.zipWithIndex.foreach { case (fragment, i) =>
        val control = Control(
          First(i == 0),
          Final(fragment.fin),
          Confirm(fragment.con),
          Unsolicited.No,
          Sequence(((seq + i) % 16).toByte))
        val headers = Message.parse(Hex.from("C0 81 00 00 " + fragment.objects)).headers
        pending.enqueue(Message(control, AppFunction.response, Some(fragment.iin), headers))
      }
    }

    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] =
      if (pending.isEmpty) timedOut(timeoutMs) else Some(pending.dequeue())
  }

  private val event = "02 01 17 01 05 81" // g2v1, index 5
  private val static = "01 02 00 00 00 81" // g1v2, index 0

  // runs every step, carrying on past failures; a manual question gets no answer (end of input), so it fails rather
  // than waits. Returns how many steps failed.
  private def failedSteps(procedure: ProcedureDefinition, dut: Dut): Int = Console.withIn(new StringReader(""))(
    procedure.configure(Options.from(Map(OutstationOptions.subsetLevel.id -> "2"))).steps.count(s =>
      Try(s.step.run(dut)).isFailure))

  test("8.5.5.2.2: the last fragment needn't ask for confirmation when it carries only static data") {
    def classes1230(fragments: Fragment*) = failedSteps(Procedure85522, new Dut(1, fragments.toList)) == 0
    classes1230(Fragment(con = true, fin = false, event), Fragment(con = false, fin = true, static)) should be(true)
    classes1230(Fragment(con = true, fin = false, event), Fragment(con = true, fin = true, static)) should be(true)
    // the fragment with events, and one followed by another, must ask for confirmation
    classes1230(Fragment(con = true, fin = false, event), Fragment(con = false, fin = true, event)) should be(false)
    classes1230(
      Fragment(con = true, fin = false, event),
      Fragment(con = false, fin = false, static),
      Fragment(con = false, fin = true, static)) should be(false)
    classes1230(
      Fragment(con = true, fin = false, event),
      Fragment(con = true, fin = false, static),
      Fragment(con = false, fin = true, static)) should be(true)
  }

  test("the input event procedures empty an event buffer whether it takes one fragment or several") {
    // the steps after emptying the buffer fail against this DUT, as many either way
    for (procedure <- List(s814.Procedure81422, s814.Procedure81423, s817.Procedure81722, s822.Procedure82223)) {
      val one = failedSteps(procedure, new Dut(1, List(Fragment(con = true, fin = true, event))))
      val two = failedSteps(
        procedure,
        new Dut(1, List(Fragment(con = true, fin = false, event), Fragment(con = true, fin = true, event))))
      withClue(procedure.id)(two should equal(one))
    }
  }

  test("8.19.2.1 empties the analog event buffer whether it takes one fragment or several") {
    val analog = "20 01 17 01 05 01 0A 00 00 00" // g32v1, index 5
    val one = failedSteps(s819.Procedure81921, new Dut(0, List(Fragment(con = true, fin = true, analog))))
    val two = failedSteps(
      s819.Procedure81921,
      new Dut(0, List(Fragment(con = true, fin = false, analog), Fragment(con = true, fin = true, analog))))
    two should equal(one)
  }

  test("the class 1 reads that check IIN bits accept events that take one fragment or several") {
    // the bits these reads check: restart, need time, broadcast
    val iin = IIN.DEVICE_RESTART | IIN.NEED_TIME | IIN.BROADCAST
    for ((procedure, seq) <- List(
        s86.s861.Procedure8612 -> 0,
        s87.s872.Procedure8722 -> 0,
        s86.s865.Procedure8652 -> 1,
        s86.s865.Procedure8653 -> 2,
        s86.s865.Procedure8654 -> 1)) {
      val one = failedSteps(procedure, new Dut(seq, List(Fragment(con = true, fin = true, event, iin))))
      val two = failedSteps(
        procedure,
        new Dut(seq, List(Fragment(con = true, fin = false, event, iin), Fragment(con = true, fin = true, event, iin))))
      withClue(procedure.id)(two should equal(one))
    }
  }

  test("8.6.4.2 and 8.6.4.3 read output status that takes one fragment or several") {
    // g10v2 at index 0, then at index 1, both online
    val (first, second) = ("0A 02 00 00 00 01", "0A 02 00 01 01 01")
    for (procedure <- List(s86.s864.Procedure8642, s86.s864.Procedure8643)) {
      val one = failedSteps(procedure, new Dut(0, List(Fragment(con = false, fin = true, s"$first $second"))))
      val two = failedSteps(
        procedure,
        new Dut(0, List(Fragment(con = true, fin = false, first), Fragment(con = false, fin = true, second))))
      withClue(procedure.id)(two should equal(one))
      // a fragment followed by another must ask for confirmation
      val noCon = failedSteps(
        procedure,
        new Dut(0, List(Fragment(con = false, fin = false, first), Fragment(con = false, fin = true, second))))
      withClue(procedure.id)(noCon should be > one)
    }
  }

  test("8.3.2 checks the operated analog output in whichever fragment of the status response reports it") {
    // g40v2 at index 5, then at index 0, the default primary analog output
    val (other, primary) = ("28 02 00 05 05 01 0A 00", "28 02 00 00 00 01 0A 00")
    def run(fragments: Fragment*): (Int, Int) = {
      val dut = new Dut(1, fragments.toList)
      (failedSteps(s83.Procedure832, dut), dut.plugin.tolerancesChecked)
    }
    val (failuresInOne, checkedInOne) = run(Fragment(con = false, fin = true, s"$other $primary"))
    run(Fragment(con = true, fin = false, other), Fragment(con = false, fin = true, primary)) should equal(
      (failuresInOne, checkedInOne))
    checkedInOne should equal(1)
    // a fragment followed by another must ask for confirmation
    run(Fragment(con = false, fin = false, other), Fragment(con = false, fin = true, primary))._1 should
      be >
      failuresInOne
  }

  test("8.5.6.2 and 8.6.6.2 empty an event buffer whether it takes one fragment or several") {
    // the steps after emptying the buffer fail against this DUT, as many either way
    for (procedure <- List(Procedure8562, Procedure86621)) {
      val one = failedSteps(procedure, new Dut(0, List(Fragment(con = true, fin = true, event))))
      val two = failedSteps(
        procedure,
        new Dut(0, List(Fragment(con = true, fin = false, event), Fragment(con = true, fin = true, event))))
      two should equal(one)
    }
  }

  test("8.6.6.2 requires the events emptied from the buffer to ask for confirmation") {
    val asks = failedSteps(Procedure86621, new Dut(0, List(Fragment(con = true, fin = true, event))))
    failedSteps(Procedure86621, new Dut(0, List(Fragment(con = false, fin = true, event)))) should be > asks
  }
}
