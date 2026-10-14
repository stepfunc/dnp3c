// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.{ CounterOptions, OutstationOptions }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group20Var1, Group20Var2, Group20Var5, Group20Var6 }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.24, with counters 0 and 1
@RunWith(classOf[JUnitRunner])
class StaticVariationsTest extends AnyFunSuite with Matchers {

  // sets RESTART on the first point, and says it should report ONLINE and RESTART, or the flags given
  private class Plugin(says: Int = 0x03) extends DefaultIntegrationPlugin {
    var abnormal: Option[Int] = None
    var normals = 0
    override def setFlagsNormal(reporter: PluginReporter, kind: PointKind): Unit = {
      abnormal = None
      normals += 1
    }
    override def setFlagAbnormal(reporter: PluginReporter, kind: PointKind, indices: Seq[Int]): AbnormalFlag = {
      abnormal = indices.headOption
      AbnormalFlag(indices.head, Some(says))
    }
  }

  private val withFlags = Map[Variation, Variation](
    Group20Var1 -> Group20Var1,
    Group20Var5 -> Group20Var1,
    Group20Var2 -> Group20Var2,
    Group20Var6 -> Group20Var2)

  /**
   * Answers each read with counters 0 and 1 in the variation given for it, flags 0x01, or 0x03 (RESTART) for an abnormal
   * point unless it hides it; by default, in the variation requested, and the abnormal point in its variation with flags
   */
  private def dut(
      plugin: Plugin,
      variation: (Variation, Int) => Variation = (v, _) => v,
      hidesAbnormal: Boolean = false,
      nullError: Boolean = false,
      dropsAbnormal: Boolean = false): ScriptedDut =
    new ScriptedDut(
      StubDriver.reporter(plugin),
      request => {
        val requested = request.headers.head.variation
        if (nullError) (IIN.OBJECT_UNKNOWN, Nil)
        else {
          val headers = List(0, 1).filterNot(i => dropsAbnormal && plugin.abnormal.contains(i)).flatMap { i =>
            val isAbnormal = plugin.abnormal.contains(i) && !hidesAbnormal
            val v = variation(if (isAbnormal) withFlags(requested) else requested, i)
            val flags = if (isAbnormal) "03 " else "01 "
            val value = v match {
              case Group20Var1 => flags + "05 00 00 00"
              case Group20Var2 => flags + "05 00"
              case Group20Var5 => "05 00 00 00"
              case _ => "05 00"
            }
            Message.parseObjectHeaders(Hex.from("14 %02X 00 %02X %02X %s".format(v.id, i, i, value)))
          }
          (IIN.empty, headers)
        }
      }
    )

  private def run(
      plugin: Plugin,
      d: ScriptedDut,
      level: Int = 3,
      settings: Map[String, String] = Map()): (Boolean, List[String]) = {
    val reporter = d.reporter
    val options = Options.from(settings + (OutstationOptions.subsetLevel.id -> level.toString))
    val passed = Try(
      StaticVariations.test(StaticVariations.counters).configure(options).steps.foreach(s =>
        s.step.run(d).foreach(_.check()))).isSuccess
    (passed, reporter.notes)
  }

  test("counters in the variations requested, the abnormal one with flags, pass, and the flags are noted") {
    val plugin = new Plugin
    val (passed, notes) = run(plugin, dut(plugin))
    passed should equal(true)
    notes.exists(_.contains("came back with flags 0x03")) should equal(true)
    plugin.abnormal should equal(None) // set back to normal at the end
  }

  test("a counter whose abnormal flag isn't reported fails, and the flags are set back to normal") {
    val plugin = new Plugin
    run(plugin, dut(plugin, hidesAbnormal = true))._1 should equal(false)
    plugin.abnormal should equal(None)
  }

  test("a 16-bit answer to a 32-bit request passes only for a counter the DUT keeps only as 16 bits") {
    val narrow =
      (v: Variation, _: Int) => if (v == Group20Var1) Group20Var2 else if (v == Group20Var5) Group20Var6 else v
    val plugin = new Plugin
    run(plugin, dut(plugin, narrow))._1 should equal(false)
    val plugin2 = new Plugin
    run(plugin2, dut(plugin2, narrow), settings = Map(CounterOptions.only16Bit.id -> "0, 1"))._1 should equal(true)
  }

  test("a level 1 or 2 device may answer with IIN2.1 and skip the test; a level 3 one may not") {
    val plugin = new Plugin
    val (passed, notes) = run(plugin, dut(plugin, nullError = true), level = 2)
    passed should equal(true)
    notes.exists(_.contains("as a subset level 1 or 2 device may")) should equal(true)
    run(new Plugin, dut(new Plugin, nullError = true), level = 3)._1 should equal(false)
  }

  test("every read must report every point, and the abnormal point the flags the plugin set") {
    val plugin = new Plugin
    run(plugin, dut(plugin, dropsAbnormal = true))._1 should equal(false)
    val plugin2 = new Plugin(says = 0x02)
    run(plugin2, dut(plugin2))._1 should equal(false)
  }
}
