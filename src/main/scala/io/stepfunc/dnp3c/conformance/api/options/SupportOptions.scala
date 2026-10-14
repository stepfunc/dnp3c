// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  BooleanOption, OptionCondition, OptionGroup, OptionSource, OptionTypes, Requirement, TestOption
}

/**
 * Registry of all optionally supported features
 */
object SupportOptions extends OptionGroup {

  trait OptionCollection {
    def allOptions: List[BooleanOption]
  }

  case class OutputOptions(shortName: String, longName: String) extends OptionCollection {

    val statusPoints: BooleanOption =
      OptionTypes.boolean(SupportOptions, s"$shortName.status", true, s"Does the DUT support $longName status points?")
    val commands: BooleanOption =
      OptionTypes.boolean(SupportOptions, s"$shortName.commands", true, s"Does the DUT support $longName points?")
    val disabling: BooleanOption = OptionTypes.boolean(
      SupportOptions,
      s"disable.$shortName",
      true,
      s"Can the device be configured to uninstall or disable all its $longName points?"
    )
    // output events (g11, g42) are a Subset Level 4 feature, so they default to unsupported
    val events: BooleanOption = OptionTypes.boolean(
      SupportOptions,
      s"${shortName}ChangeEvents",
      false,
      s"Does the DUT support $longName change events?")

    override def allOptions: List[BooleanOption] = List(statusPoints, commands, disabling, events)

  }

  case class InputOptions(shortName: String, longName: String) extends OptionCollection {

    val points: BooleanOption =
      OptionTypes.boolean(SupportOptions, s"$shortName", true, s"Does the DUT support $longName points?")
    val disabling: BooleanOption = OptionTypes.boolean(
      SupportOptions,
      s"disable.$shortName",
      true,
      s"Can the device be configured to uninstall or disable all its $longName points?"
    )
    val events: BooleanOption = OptionTypes.boolean(
      SupportOptions,
      s"${shortName}ChangeEvents",
      true,
      s"Does the DUT support $longName change events?")

    // 3.1r1: a subset level 1 device needn't support reading the event objects, and then answers with an error
    val eventReads: BooleanOption = OptionTypes.boolean(
      SupportOptions,
      s"${shortName}EventReads",
      true,
      s"Can the DUT's $longName change events be read? A subset level 1 device needn't support it; at levels 2 and " +
        "3 it must"
    )

    // 3.1r1: a subset level 1 device needn't support reading the static objects either
    val reads: BooleanOption = OptionTypes.boolean(
      SupportOptions,
      s"${shortName}Reads",
      true,
      s"Can the DUT's $longName points be read? A subset level 1 device needn't support it; at levels 2 and 3 it must"
    )

    override def allOptions: List[BooleanOption] = List(points, disabling, events, eventReads, reads)

    // the DUT has these points and their events (events without the points can't be tested)
    def withEvents: List[OptionCondition] = List(points, events)

    // the DUT has these points and can uninstall or disable them
    def withDisabling: List[OptionCondition] = List(points, disabling)

    // for a procedure about a DUT without these events (or points): it doesn't run on one that has both
    def withoutEvents: Requirement = Requirement.not(Requirement.all(OptionCondition(points), OptionCondition(events)))
  }

  object Inputs {

    val binary: InputOptions = InputOptions("binaryInput", "Binary Input")
    val doubleBit: InputOptions = InputOptions("doubleBit", "Double Bit")
    val counter: InputOptions = InputOptions("counter", "Counter")
    val frozenCounter: InputOptions = InputOptions("frozenCounter", "Frozen Counter")
    val analog: InputOptions = InputOptions("analog", "Analog Input")

    val allOptions: List[BooleanOption] = List(binary, doubleBit, counter, frozenCounter, analog).flatMap(_.allOptions)
  }

  object Outputs {
    val binary: OutputOptions = OutputOptions("binaryOutput", "Binary Output")
    val analog: OutputOptions = OutputOptions("analogOutput", "Analog Output")

    val allOptions: List[BooleanOption] = List(binary, analog).flatMap(_.allOptions)
  }

  val dataLinkConfirmRequests: BooleanOption = OptionTypes.boolean(
    this,
    "dataLinkConfirmRequests",
    true,
    "Can the DUT be configured to request data link layer confirmation?")

  val dataLinkRetries: BooleanOption = OptionTypes.boolean(
    this,
    "dataLinkRetries",
    true,
    "Does the DUT retry primary transmissions when a data link layer confirmation is not received?")

  val analogEventsQueuedPerPoint: BooleanOption = OptionTypes.boolean(
    this,
    "analogEventsQueuedPerPoint",
    true,
    "Does the DUT queue multiple Analog Input change events for the same point?")

  val counterEventsQueuedPerPoint: BooleanOption = OptionTypes.boolean(
    this,
    "counterEventsQueuedPerPoint",
    true,
    "Does the DUT queue multiple Counter change events for the same point?")

  val multiFragmentResponses: BooleanOption = OptionTypes.boolean(
    this,
    "multiFragmentResponses",
    true,
    "Can the DUT be made to send a multi-fragment response (a large Class 0 response, or enough event data)?")

  val selfAddress: BooleanOption =
    OptionTypes.boolean(this, "selfAddress", false, "Does the DUT support link-layer self addressing?")

  val noClassAssignment: BooleanOption = OptionTypes.boolean(
    this,
    "noClassAssignment",
    true,
    "Is the DUT configurable such that points can be removed from all Classes (i.e. points belong to no Class)?")

  val globalRemoteSupervisoryControl: BooleanOption = OptionTypes.boolean(
    this,
    "globalRemoteSupervisoryControl",
    true,
    "Can the DUT put its binary outputs under local control?")

  val individualRemoteSupervisoryControl: BooleanOption = OptionTypes.boolean(
    this,
    "individualRemoteSupervisoryControl",
    true,
    "Can the DUT switch individual binary outputs to local control?")

  val configurableFragmentSize: BooleanOption =
    OptionTypes.boolean(this, "configurableFragmentSize", false, "Can the DUT's fragment size be configured?")

  val unsolicitedResponse: BooleanOption =
    OptionTypes.boolean(this, "unsolicitedResponse", true, "Does the DUT support unsolicited responses?")

  val deviceAttributes: BooleanOption =
    OptionTypes.boolean(this, "deviceAttributes", true, "Does the DUT support device attributes (group 0)?")

  val rangeReads: BooleanOption = OptionTypes.boolean(
    this,
    "rangeReads",
    true,
    "Does the DUT answer reads of binary inputs, binary output status, counters, frozen counters, analog inputs and " +
      "analog output status with a start/stop range (qualifiers 0x00 and 0x01)? Subset level 3 requires it (3.1r1's " +
      "8.23)"
  )

  val multipleAddresses: BooleanOption = OptionTypes.boolean(
    this,
    "multipleAddresses",
    false,
    "Does the DUT answer to more than one DNP3 address over the TCP connection (link.otherDestination)? 3.1r1's " +
      "6.6.2-4 runs over TCP only for such a DUT"
  )

  override def prefix: String = "supports"
  override def description: String = "Optionally supported DNP3 features"

  override def options: List[TestOption] = Inputs.allOptions ::: Outputs.allOptions ::: List(
    dataLinkConfirmRequests,
    dataLinkRetries,
    analogEventsQueuedPerPoint,
    counterEventsQueuedPerPoint,
    multiFragmentResponses,
    selfAddress,
    noClassAssignment,
    globalRemoteSupervisoryControl,
    individualRemoteSupervisoryControl,
    configurableFragmentSize,
    unsolicitedResponse,
    deviceAttributes,
    rangeReads,
    multipleAddresses
  )
}
