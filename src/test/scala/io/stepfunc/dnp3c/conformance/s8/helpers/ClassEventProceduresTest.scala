// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, ClassOptions }
import io.stepfunc.dnp3c.conformance.s8.s85.{ s852, s853, s854 }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group60Var2, Group60Var3, Group60Var4 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// the class 1, 2 and 3 data procedures written once in ClassEventProcedures
@RunWith(classOf[JUnitRunner])
class ClassEventProceduresTest extends AnyFunSuite with Matchers {

  private case class Family(
      support: String,
      variation: Variation,
      allQualifier: ProcedureDefinition,
      count: ProcedureDefinition)

  private val families = List(
    Family(ClassOptions.supportClass1.id, Group60Var2, s852.Procedure85221, s852.Procedure85222),
    Family(ClassOptions.supportClass2.id, Group60Var3, s853.Procedure85321, s853.Procedure85322),
    Family(ClassOptions.supportClass3.id, Group60Var4, s854.Procedure85421, s854.Procedure85422)
  )

  private def runs(procedure: ProcedureDefinition, settings: (String, String)*): Boolean =
    procedure.disabledBy(Options.from(settings.toMap)).isEmpty

  test("each class's procedures need only that class, except 0x06, which runs without it") {
    for (family <- families; other <- families) {
      runs(family.count, other.support -> "false") should equal(family != other)
      runs(family.allQualifier, other.support -> "false") should equal(true)
    }
  }

  test("each class's 0x06 procedure reads that class's events") {
    for (family <- families) {
      val requests = mutable.ListBuffer.empty[Message]
      val driver = new StubDriver(StubDriver.reporter()) {
        override def writeMessage(message: Message, options: LinkOverride): Unit = requests += message
      }
      family.allQualifier.configure(
        Options.from(Map(OutstationOptions.subsetLevel.id -> "2"))).steps.head.step.run(driver)
      requests.flatMap(_.headers.map(_.variation)) should equal(List(family.variation))
    }
  }
}
