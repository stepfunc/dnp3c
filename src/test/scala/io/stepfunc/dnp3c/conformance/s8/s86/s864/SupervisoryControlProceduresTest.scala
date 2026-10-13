// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s864

import java.io.StringReader

import scala.collection.mutable
import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.{ Index, Options, ProcedureDefinition }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Message, ObjectHeader }
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 8.6.4.2 and 8.6.4.3 run against a simulated outstation with three binary outputs
@RunWith(classOf[JUnitRunner])
class SupervisoryControlProceduresTest extends AnyFunSuite with Matchers {

  private val options = Options.from(Map())

  /**
   * The outputs' local mode, and the controls they carried out
   *
   * @param localForced whether an output in local mode reports ONLINE and LOCAL_FORCED, as the replaced 8.6.4.3
   *                    expected, rather than clearing ONLINE
   */
  private class Outstation(localForced: Boolean) {
    val local = mutable.Set.empty[Int]
    val operated = mutable.ListBuffer.empty[Int]
    private val states = mutable.Map(0 -> true, 1 -> false, 2 -> false)

    private def flags(i: Int): Int = {
      val state = if (states(i)) 0x80 else 0
      if (!local(i)) 0x01 | state
      else if (localForced) 0x11 | state
      else state
    }

    private def iin: IIN = if (local.nonEmpty) IIN.LOCAL_CONTROL else IIN.empty

    def answer(request: Message): (IIN, List[ObjectHeader]) = request.function match {
      case AppFunction.read =>
        val g10 = Message.parse(Hex.from("C0 81 00 00 0A 02 00 00 02 " + (0 to 2).map(i =>
          "%02X".format(flags(i))).mkString(" ")))
        (iin, g10.headers)
      case AppFunction.directOp =>
        val index = request.headers.flatMap(_.toBytes).apply(4) & 0xFF // g12v1, 1-octet count, then the index
        val status = if (local(index)) CommandStatus.LOCAL else CommandStatus.SUCCESS
        if (!local(index)) {
          operated += index
          states(index) = !states(index)
        }
        (iin, List(SupervisoryControl.control(options, Index(index), status)))
      case other => throw new Exception(s"unexpected request: $other")
    }
  }

  private class Plugin(outstation: Outstation) extends DefaultIntegrationPlugin {
    private var checked = 0
    private def operations: List[Int] = {
      val since = outstation.operated.drop(checked).toList
      checked = outstation.operated.size
      since
    }
    override def enableRemoteSupervisoryControl(reporter: PluginReporter): Unit = outstation.local.clear()
    override def setGlobalRemoteSupervisoryControl(reporter: PluginReporter): Unit = outstation.local ++= 0 to 2
    override def setIndividualRemoteSupervisoryControl(reporter: PluginReporter, index: Int): Unit =
      outstation.local += index
    override def checkBinaryOutputOperate(reporter: CheckReporter, index: Int): Option[DeferredCheck] = {
      if (operations != List(index)) throw new Exception(s"binary output $index didn't operate")
      None
    }
    override def checkBinaryOutputNotOperate(reporter: CheckReporter): Option[DeferredCheck] = {
      if (operations.nonEmpty) throw new Exception("a binary output operated")
      None
    }
  }

  // runs the steps in order, stopping at the first that fails
  private def passes(procedure: ProcedureDefinition, localForced: Boolean = false): Boolean = {
    val outstation = new Outstation(localForced)
    val dut = new ScriptedDut(StubDriver.reporter(new Plugin(outstation)), outstation.answer)
    Console.withIn(new StringReader(""))(
      Try(procedure.configure(options).steps.foreach(s => s.step.run(dut).foreach(_.check()))).isSuccess)
  }

  test("8.6.4.2 passes an outstation whose outputs in local mode clear ONLINE and refuse controls") {
    passes(Procedure8642) should equal(true)
  }

  test("8.6.4.3 passes an outstation whose output in local mode clears ONLINE, while another stays remote") {
    passes(Procedure8643) should equal(true)
  }

  test("8.6.4.3 fails an outstation that sets LOCAL_FORCED in local mode, as the replaced test expected") {
    passes(Procedure8643, localForced = true) should equal(false)
  }
}
