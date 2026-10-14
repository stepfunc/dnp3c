// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.v27
import io.stepfunc.dnp3c.conformance.v27.Procedure8_5_5_2_2
import io.stepfunc.dnp3c.conformance.v27.Procedure8_5_6_2
import io.stepfunc.dnp3c.conformance.v27.Procedure8_6_6_2_1
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.io.StringReader

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
      extends RespondingDriver(StubDriver.reporter(plugin)) {

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val requestSeq = message.ctrl.seq.value.toInt
      if (message.function == AppFunction.confirm) ()
      else if (requestSeq != seq)
        enqueue(Message(Control.request(message.ctrl.seq), AppFunction.response, Some(IIN.empty), Nil))
      else fragments.zipWithIndex.foreach { case (fragment, i) =>
        val control = Control(
          First(i == 0),
          Final(fragment.fin),
          Confirm(fragment.con),
          Unsolicited.No,
          Sequence(((seq + i) % 16).toByte))
        val headers = Message.parse(Hex.from("C0 81 00 00 " + fragment.objects)).headers
        enqueue(Message(control, AppFunction.response, Some(fragment.iin), headers))
      }
    }

  }

  private val event = "02 01 17 01 05 81" // g2v1, index 5
  private val static = "01 02 00 00 00 81" // g1v2, index 0

  // runs every step, carrying on past failures; a manual question gets no answer (end of input), so it fails rather
  // than waits. Returns how many steps failed.
  private def failedSteps(procedure: ProcedureDefinition, dut: Dut): Int =
    Console.withIn(new StringReader(""))(Run.everyStep(procedure, dut, Map(OutstationOptions.subsetLevel.id -> "2")))

  test("8.5.5.2.2: the last fragment needn't ask for confirmation when it carries only static data") {
    def classes1230(fragments: Fragment*) = failedSteps(Procedure8_5_5_2_2, new Dut(1, fragments.toList)) == 0
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
    for (procedure <- List(
        v27.Procedure8_14_2_2,
        v27.Procedure8_14_2_3,
        v27.Procedure8_17_2_2,
        v27.Procedure8_22_2_3)) {
      val one = failedSteps(procedure, new Dut(1, List(Fragment(con = true, fin = true, event))))
      val two = failedSteps(
        procedure,
        new Dut(1, List(Fragment(con = true, fin = false, event), Fragment(con = true, fin = true, event))))
      withClue(procedure.id)(two should equal(one))
    }
  }

  test("8.19.2.1 empties the analog event buffer whether it takes one fragment or several") {
    val analog = "20 01 17 01 05 01 0A 00 00 00" // g32v1, index 5
    val one = failedSteps(v27.Procedure8_19_2_1, new Dut(0, List(Fragment(con = true, fin = true, analog))))
    val two = failedSteps(
      v27.Procedure8_19_2_1,
      new Dut(0, List(Fragment(con = true, fin = false, analog), Fragment(con = true, fin = true, analog))))
    two should equal(one)
  }

  test("the class 1 reads that check IIN bits accept events that take one fragment or several") {
    // the bits these reads check: restart, need time, broadcast
    val iin = IIN.DEVICE_RESTART | IIN.NEED_TIME | IIN.BROADCAST
    for ((procedure, seq) <- List(
        v27.Procedure8_6_1_2 -> 0,
        v27.Procedure8_7_2_2 -> 0,
        v27.Procedure8_6_5_2 -> 1,
        v27.Procedure8_6_5_3 -> 2,
        v27.Procedure8_6_5_4 -> 1)) {
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
    for (procedure <- List(v27.Procedure8_6_4_2, v27.Procedure8_6_4_3)) {
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
      (failedSteps(v27.Procedure8_3_2, dut), dut.plugin.tolerancesChecked)
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
    for (procedure <- List(Procedure8_5_6_2, Procedure8_6_6_2_1)) {
      val one = failedSteps(procedure, new Dut(0, List(Fragment(con = true, fin = true, event))))
      val two = failedSteps(
        procedure,
        new Dut(0, List(Fragment(con = true, fin = false, event), Fragment(con = true, fin = true, event))))
      two should equal(one)
    }
  }

  test("8.6.6.2 requires the events emptied from the buffer to ask for confirmation") {
    val asks = failedSteps(Procedure8_6_6_2_1, new Dut(0, List(Fragment(con = true, fin = true, event))))
    failedSteps(Procedure8_6_6_2_1, new Dut(0, List(Fragment(con = false, fin = true, event)))) should be > asks
  }
}
