// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.dnp3.app.objects._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class SubsetLevelTestSuite extends AnyFunSuite with Matchers {

  test("Subset Level 2 static fits with table 8-3 of test spec") {
    val table83 = List(
      Group1Var1,
      Group1Var2,
      // Group3Var1, Double-bit Inputs are part of Subset Level 4
      // Group3Var2, Double-bit Inputs are part of Subset Level 4
      Group10Var2,
      Group20Var1,
      Group20Var2,
      Group20Var5,
      Group20Var6,
      Group21Var1,
      Group21Var2,
      Group21Var9,
      Group21Var10,
      Group30Var1,
      Group30Var2,
      Group30Var3,
      Group30Var4,
      Group40Var2
    )

    val variations = Group.list.flatMap(g => g.variations) // Get all group/variations
      .filter(v => SubsetLevel.isLevelOneOrTwo(v.minSubsetLevel)) // Filter subset level 1 and 2
      .filter(v => v.id != 0) // Remove "Any" variations
      .filter(v => v.objectType == Static) // Filter static only

    table83 should contain theSameElementsAs variations
  }

  test("Subset Level 2 event fits with table 8-4 of test spec") {
    val table84 = List(
      Group2Var1,
      Group2Var2,
      Group2Var3,
      // Group4Var1, Double-bit Inputs are part of Subset Level 4
      // Group4Var2, Double-bit Inputs are part of Subset Level 4
      // Group4Var3, Double-bit Inputs are part of Subset Level 4
      Group22Var1,
      Group22Var2,
      Group32Var1,
      Group32Var2
      // Group51Var1, This should be checked manually
      // Group51Var2  This should be checked manually
    )

    val variations = Group.list.flatMap(g => g.variations) // Get all group/variations
      .filter(v => SubsetLevel.isLevelOneOrTwo(v.minSubsetLevel)) // Filter subset level 1 and 2
      .filter(v => v.id != 0) // Remove "Any" variations
      .filter(v => v.objectType == Event) // Filter static only

    table84 should contain theSameElementsAs variations
  }

  test("Subset Level 2 fits with table 8-5 of test spec") {
    val table85 = List(
      Group20Var1,
      Group20Var2,
      Group20Var5,
      Group20Var6
    )

    val variations = Group.list.flatMap(g => g.variations) // Get all group/variations
      .filter(v => SubsetLevel.isLevelOneOrTwo(v.minSubsetLevel)) // Filter subset level 1 and 2
      .filter(v => v.group == Group20) // Group 20 only
      .filter(v => v.id != 0) // Remove "Any" variations

    table85 should contain theSameElementsAs variations
  }

  test("Subset Level 2 fits with table 8-6 of test spec") {
    val table86 = List(
      Group21Var1,
      Group21Var2,
      Group21Var9,
      Group21Var10
    )

    val variations = Group.list.flatMap(g => g.variations) // Get all group/variations
      .filter(v => SubsetLevel.isLevelOneOrTwo(v.minSubsetLevel)) // Filter subset level 1 and 2
      .filter(v => v.group == Group21) // Group 20 only
      .filter(v => v.id != 0) // Remove "Any" variations

    table86 should contain theSameElementsAs variations
  }
}
