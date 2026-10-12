// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class PluginIsolationTest extends AnyFunSuite with Matchers {

  // records which of its methods were called
  private class Plugin extends DefaultIntegrationPlugin {
    val calls = mutable.ListBuffer.empty[String]
    override def cyclePower(reporter: PluginReporter): Unit = calls += "cyclePower"
    override def verifyTimestamp(
        reporter: CheckReporter,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
      calls += "verifyTimestamp"
      None
    }
  }

  private class Driver(reporter: ProcedureReporter) extends StubDriver(reporter) {
    override def close(): Unit = {}
  }

  test("each procedure's steps and validations reach only its own plugin") {
    val (first, second) = (new Plugin, new Plugin)
    val firstDriver = new Driver(StubDriver.reporter(first))
    val secondDriver = new Driver(StubDriver.reporter(second))
    val response = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), Nil)

    PluginSteps.cyclePower.run(firstDriver)
    PluginValidations.verifyTimestamp().validate(secondDriver.reporter, response)
    PluginValidations.verifyTimestamp().validate(firstDriver.reporter, response)

    first.calls.toList should equal(List("cyclePower", "verifyTimestamp"))
    second.calls.toList should equal(List("verifyTimestamp"))
  }
}
