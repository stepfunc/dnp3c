// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.dnp3.app.objects.Group23Var2
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class GroupVariationTestSuite extends AnyFunSuite with Matchers {

  test("a response with frozen counter events parses") {
    // g23v2 (16-bit frozen counter event), qualifier 0x17, index 5, ONLINE, value 42
    val message = Message.parse(Hex.from("C0 81 00 00 17 02 17 01 05 01 2A 00"))

    message.headers.map(_.variation) should equal(List(Group23Var2))
    message.iterate.collect { case GenIndexedPoint(idx, p: CounterPoint) => (idx, p.value) } should equal(Seq((5, 42L)))
  }

}
