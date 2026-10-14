// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  IndexListOption, IndexOption, Index, IntegerOption, OptionGroup, OptionTypes, RequiredIntegerOption
}

// options that several kinds of point have, built the same way for each, in the kind's group
private[options] object PointOptions {

  // e.g. "Binary Input"
  def numEventsGenerated(group: OptionGroup, events: String): IntegerOption = OptionTypes.integer(
    group,
    "numEventsGenerated",
    3,
    s"Number of $events Events generated for the 0x07/0x08 count-limited tests (at least 2, so the count limit is " +
      "exercised).",
    Some(2),
    Some(255)
  )

  def eventsClass(group: OptionGroup, events: String): IntegerOption =
    OptionTypes.integer(group, "eventsClass", 1, s"$events Events class assignment.", Some(1), Some(3))

  // e.g. "binary input", of event group 2
  def defaultEventVariation(group: OptionGroup, events: String, eventGroup: Int): RequiredIntegerOption =
    OptionTypes.requiredInteger(
      group,
      "defaultEventVariation",
      s"The Device Profile's default variation for $events change events (g$eventGroup): what 3.1r1 checks a read " +
        s"of g$eventGroup variation 0, or of a class, against",
      List(1, 2, 3)
    )

  // points kept and reported at one width only, e.g. "Counters", under the prefix, e.g. "frozen."
  def onlyWidth(group: OptionGroup, prefix: String, points: String, bits: Int): IndexListOption = {
    val other = if (bits == 16) 32 else 16
    OptionTypes.indexList(
      group,
      s"${prefix}only${bits}Bit",
      s"$points the DUT keeps and reports only as $bits-bit values, which 3.1r1's 8.24 accepts in a $bits-bit " +
        s"variation where a $other-bit one is requested. Empty for none",
      Nil
    )
  }

  // an output's, e.g. "BO" and "binary output"
  def installedPoints(group: OptionGroup, abbreviation: String): IndexListOption =
    OptionTypes.indexList(group, "installedPoints", s"List of all installed $abbreviation indices")

  def uninstalledIndex(group: OptionGroup, points: String): IndexOption =
    OptionTypes.index(group, "uninstalled.index", Index.max, s"Index not assigned to any $points point")

  def maxPointsInSingleRequest(group: OptionGroup, points: String): IntegerOption = OptionTypes.integer(
    group,
    "maxPointsInSingleRequest",
    1,
    s"Maximum number of $points points that are supported in a single request",
    Some(1),
    None)

  // the output status flags the plugin can change, e.g. "4 LOCAL_FORCED" the last, which the procedure changes
  def changeableFlags(group: OptionGroup, points: String, last: String, procedure: String): IndexListOption =
    OptionTypes.indexList(
      group,
      "status.changeableFlags",
      s"The $points status flags, by bit number (0 ONLINE to $last), that the plugin can change, which 3.1r1's " +
        s"$procedure changes one at a time. Empty for none",
      Nil
    )
}
