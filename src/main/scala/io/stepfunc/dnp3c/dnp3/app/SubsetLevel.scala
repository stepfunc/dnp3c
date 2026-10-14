// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

sealed trait SubsetLevel

object SubsetLevel {

  object One extends SubsetLevel
  object Two extends SubsetLevel
  object Three extends SubsetLevel
  object Four extends SubsetLevel
  object None extends SubsetLevel

  private val oneAndTwo = Set(One, Two)

  def isLevelOneOrTwo(level: SubsetLevel): Boolean = oneAndTwo.contains(level)

  /** whether a device of the subset level numbered `level` may use what's in `lowest` and above */
  def within(lowest: SubsetLevel, level: Int): Boolean = lowest match {
    case One => level >= 1
    case Two => level >= 2
    case Three => level >= 3
    case Four => level >= 4
    case SubsetLevel.None => false
  }
}
