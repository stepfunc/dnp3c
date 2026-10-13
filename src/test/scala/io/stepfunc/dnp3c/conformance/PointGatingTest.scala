// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.s8.{ s813, s814, s816, s817, s819, s821, s822 }
import io.stepfunc.dnp3c.conformance.s8.s86.s866
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// a procedure about an input's events or disabling runs only for a DUT that has those points
@RunWith(classOf[JUnitRunner])
class PointGatingTest extends AnyFunSuite with Matchers {

  private def runs(
      procedure: ProcedureDefinition,
      input: SupportOptions.InputOptions,
      points: Boolean,
      feature: (SupportOptions.InputOptions => api.BooleanOption, Boolean)): Boolean =
    procedure
      .disabledBy(Options.from(Map(input.points.id -> points.toString, feature._1(input).id -> feature._2.toString)))
      .isEmpty

  // (input, procedures that test its events, procedures for a DUT without them)
  private val eventFamilies = List(
    (Inputs.binary, List(s814.Procedure81422, s866.Procedure86621), List(s814.Procedure81421)),
    (Inputs.doubleBit, List(s822.Procedure82222, s866.Procedure86624), List(s822.Procedure82221)),
    (Inputs.counter, List(s817.Procedure81722, s866.Procedure86623), List(s817.Procedure81721)),
    (Inputs.analog, List(s819.Procedure81922, s866.Procedure86622), Nil)
  )

  test("event procedures need the points and their events; the procedure without them runs otherwise") {
    for ((input, withEvents, without) <- eventFamilies; points <- List(true, false); events <- List(true, false)) {
      val both = points && events
      withClue(s"${input.events.id} points=$points events=$events: ") {
        withEvents.foreach(p => runs(p, input, points, (_.events, events)) should equal(both))
        without.foreach(p => runs(p, input, points, (_.events, events)) should equal(!both))
      }
    }
  }

  test("8.15.2 needs binary inputs, their events and relative time") {
    val settings = for (points <- List(true, false); events <- List(true, false); relative <- List(true, false))
      yield (points, events, relative)
    for ((points, events, relative) <- settings) {
      val options = Options.from(Map(
        Inputs.binary.points.id -> points.toString,
        Inputs.binary.events.id -> events.toString,
        api.options.TimeSyncOptions.binaryInputChangeWithRelativeTime.id -> relative.toString
      ))
      withClue(s"points=$points events=$events relative=$relative: ")(
        s8.s815.Procedure8152.disabledBy(options).isEmpty should equal(points && events && relative))
    }
  }

  test("configurable device procedures need the points and disabling") {
    for ((input, procedure) <- List(
        Inputs.binary -> s813.Procedure81323,
        Inputs.doubleBit -> s821.Procedure82122,
        Inputs.counter -> s816.Procedure816123);
      points <- List(true, false); disabling <- List(true, false))
      withClue(s"${procedure.id} points=$points disabling=$disabling: ")(
        runs(procedure, input, points, (_.disabling, disabling)) should equal(points && disabling))
  }
}
