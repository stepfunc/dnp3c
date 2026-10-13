// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class RequirementTest extends AnyFunSuite with Matchers {

  private val x: BooleanOption = SupportOptions.selfAddress
  private val y: BooleanOption = SupportOptions.dataLinkRetries

  private def options(values: (BooleanOption, Boolean)*): Options =
    Options.from(values.map((o, v) => o.id -> v.toString).toMap)

  test("all: the first that isn't met is the reason") {
    val r = Requirement.all(OptionCondition(x), OptionCondition(y))
    r.unmet(options(x -> true, y -> true)) should equal(None)
    r.unmet(options(x -> false, y -> false)).map(_.requirement.describe) should equal(Some(s"${x.id} == true"))
    r.describe should equal(s"${x.id} == true and ${y.id} == true")
  }

  test("any of: unmet only when none is met, with the value of every option involved") {
    val r = Requirement.anyOf(OptionCondition(x), OptionCondition(y))
    r.unmet(options(x -> false, y -> true)) should equal(None)
    val unmet = r.unmet(options(x -> false, y -> false)).get
    unmet.values should equal(List(x -> false, y -> false))
    unmet.message should equal(s"it needs ${x.id} == true or ${y.id} == true")
  }

  test("not: unmet when the requirement is met") {
    val r = Requirement.not(Requirement.all(OptionCondition(x), OptionCondition(y)))
    r.unmet(options(x -> true, y -> false)) should equal(None)
    r.unmet(options(x -> true, y -> true)).map(_.values) should equal(Some(List(x -> true, y -> true)))
    r.describe should equal(s"not (${x.id} == true and ${y.id} == true)")
  }

  test("any of nothing is a mistake, and a requirement may involve no options") {
    an[IllegalArgumentException] should be thrownBy Requirement.anyOf()
    Requirement.not(Requirement.none).unmet(options()).map(_.values) should equal(Some(Nil))
  }

  test("an option condition explains itself as before") {
    OptionCondition(x).unmet(options(x -> false)).get.message should equal(s"${x.id} != true: ${x.description}")
    Requirement.none.unmet(options()) should equal(None)
  }
}
