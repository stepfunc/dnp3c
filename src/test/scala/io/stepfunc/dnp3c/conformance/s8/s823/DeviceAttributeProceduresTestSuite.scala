// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s823

import io.stepfunc.dnp3c.conformance.api.{ OptionSource, Options }
import io.stepfunc.dnp3c.conformance.api.options.{ AttributeOptions, SupportOptions }
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.Group0
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, RespondingDriver, StubDriver }
import io.stepfunc.dnp3c.protocol.parsing.UInt8
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class DeviceAttributeProceduresTestSuite extends AnyFunSuite with Matchers {

  private val procedures = List(Procedure82321, Procedure82322, Procedure82323, Procedure82324)

  private def options(values: (String, String)*): OptionSource = Options.from(values.toMap)

  test("Device attribute procedures are disabled when the DUT doesn't support device attributes") {
    procedures.foreach { p =>
      p.configure(options(SupportOptions.deviceAttributes.id -> "false")).disabledBy should be(defined)
    }
  }

  test("Device attribute procedures require the Device Profile's attributes") {
    procedures.foreach(p => an[Exception] should be thrownBy p.configure(options()))
  }

  test("8.23.2.4 writes the user-assigned system name (g0v208)") {
    // a DUT whose only attribute is g0v208; records what it's asked to do
    class Dut extends RespondingDriver(StubDriver.reporter()) {
      val functions = mutable.ListBuffer.empty[AppFunction]
      override def writeMessage(message: Message, options: LinkOverride): Unit = {
        functions += message.function
        val attribute = OneByteStartStopHeader(
          Group0(208),
          UInt8(0),
          UInt8(0),
          FieldHeaderPayload(FieldHeaderPayloadObject(Group0.visibleString("station"))))
        respond(message, IIN.empty, if (message.function == AppFunction.read) List(attribute) else Nil)
      }
    }
    val dut = new Dut
    val settings = options(
      SupportOptions.deviceAttributes.id -> "true",
      AttributeOptions.supportedVariations.id -> "208",
      AttributeOptions.writableVariations.id -> "208")
    Procedure82324.configure(settings).steps.foreach(s => Try(s.step.run(dut)))
    dut.functions should contain(AppFunction.write)
  }
}
