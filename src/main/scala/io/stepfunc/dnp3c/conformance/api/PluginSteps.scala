// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api
import io.stepfunc.dnp3c.dnp3.app.EventClass
import io.stepfunc.dnp3c.dnp3.{
  ActionTiming, Comment, InputKind, IntegrationPlugin, OutputKind, PluginReporter, PointKind, TestDriver
}
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object PluginSteps {

  // a step that has the plugin act on the DUT
  def action(desc: Comment*)(act: (IntegrationPlugin, PluginReporter) => Unit): TestStep =
    new TestStep {
      override def description: List[Comment] = desc.toList

      override def run(driver: TestDriver): List[DeferredCheck] = {
        act(driver.reporter.plugin, driver.reporter.pluginReporter())
        Nil
      }
    }

  def cyclePower: TestStep = new TestStep {
    override def description: List[Comment] = List("Cycle power to the DUT")

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.close()
      driver.reporter.plugin.cyclePower(driver.reporter.pluginReporter())
      Nil
    }
  }

  def setLinkLayerConfirm(value: Boolean): TestStep =
    action(s"${enable(value)} data link confirmation when transmitting") { (plugin, reporter) =>
      plugin.setLinkLayerConfirm(reporter, value)
    }

  def setLinkLayerSelfAddress(value: Boolean): TestStep =
    action(s"${enable(value)} data link self-address support") { (plugin, reporter) =>
      plugin.setSelfAddressSupport(reporter, value)
    }

  private def enable(value: Boolean): String = if (value) "Enable" else "Disable"

  // uninstalls or disables every point of the kind, until reinstallPoints
  def uninstall(kind: PointKind): TestStep = action(s"Uninstall or disable every ${uninstalled(kind)} on the DUT") {
    (plugin, reporter) =>
      plugin.uninstall(reporter, kind)
  }

  // each kind as the steps have always named it
  private def uninstalled(kind: PointKind): String = kind match {
    case PointKind.BinaryInput => "binary input point"
    case PointKind.DoubleBitInput => "double-bit binary input point"
    case PointKind.BinaryOutputStatus => "binary output point"
    case PointKind.Counter => "counter point"
    case PointKind.FrozenCounter => "frozen counter"
    case PointKind.AnalogInput => "analog input"
    case PointKind.AnalogOutputStatus => "analog output point"
  }

  // the points uninstalled, and the restore that reinstalls them
  def removal(kind: PointKind): Undoable = Undoable(uninstall(kind), reinstallPoints)

  def checkOperate(kind: OutputKind, indices: Iterable[Int]): PluginCheck =
    PluginCheck(indices.map(idx => Comment(s"Check that ${control(kind)} point $idx operate")).toSeq*)(
      (plugin, reporter) => indices.toList.flatMap(idx => plugin.checkOperate(reporter, kind, idx)))

  def checkOperate(kind: OutputKind, index: Int): PluginCheck =
    PluginCheck(s"Check that ${control(kind)} point $index operate")((plugin, reporter) =>
      plugin.checkOperate(reporter, kind, index))

  def checkNotOperate(kind: OutputKind): PluginCheck =
    PluginCheck(s"Check that no ${control(kind)} point operated")((plugin, reporter) =>
      plugin.checkNotOperate(reporter, kind))

  private def control(kind: OutputKind): String = kind match {
    case PointKind.BinaryOutputStatus => "binary control"
    case PointKind.AnalogOutputStatus => "analog control"
  }

  def reinstallPoints: TestStep =
    action("Reinstall or re-enable the points that were uninstalled or disabled, so later procedures can use them") {
      (plugin, reporter) =>
        plugin.reinstallPoints(reporter)
    }

  def generateClassEvents(eventClass: EventClass): TestStep = action(s"Generate known ${eventClass} event data") {
    (plugin, reporter) =>
      plugin.generateClassEvents(reporter, eventClass)
  }

  def generateExactClassEvents(eventClass: EventClass, numEvents: Int): TestStep =
    action(s"Generate $numEvents known $eventClass event(s)") { (plugin, reporter) =>
      plugin.generateExactClassEvents(reporter, eventClass, numEvents)
    }

  // events of each of the classes, in order
  def generateClassEvents(classes: List[EventClass]): TestStep = classes match {
    case Nil => describe("The DUT supports no other event class, so no events are generated")
    case _ =>
      action(s"Generate ${classes.mkString(", ")} events") { (plugin, reporter) =>
        classes.foreach(e => plugin.generateClassEvents(reporter, e))
      }
  }

  // Binary Inputs
  def generateBinaryInputPattern(): TestStep = action("Set the binary inputs to a known pattern") {
    (plugin, reporter) =>
      plugin.generatePattern(reporter, PointKind.BinaryInput)
  }

  def generateBinaryInputChangeEvents(): TestStep =
    binaryEvents("Change binary inputs several times, one or more of them, to create events") {
      (plugin, reporter) =>
        plugin.generateInputChanges(reporter, PointKind.BinaryInput)
    }

  def generateExactBinaryInputChangeEvents(numEvents: Int): TestStep =
    binaryEvents(s"Generate ${numEvents} Binary Input Change Event(s)") { (plugin, reporter) =>
      plugin.generateExactInputEvents(reporter, PointKind.BinaryInput, numEvents)
    }

  // a step that generates binary or double-bit input events, recording the time quality the plugin reports for the
  // DUT's clock before and after, for the checks of how the events are reported (IntegrationPlugin.timeQuality)
  private def binaryEvents(desc: Comment*)(generate: (IntegrationPlugin, PluginReporter) => Unit): TestStep =
    new TestStep {
      override def description: List[Comment] = desc.toList

      override def run(driver: TestDriver): List[DeferredCheck] = {
        val plugin = driver.reporter.plugin
        val before = plugin.timeQuality(driver.reporter.pluginReporter())
        generate(plugin, driver.reporter.pluginReporter())
        val after = plugin.timeQuality(driver.reporter.pluginReporter())
        driver.reporter.recordEventClock(before, after)
        (before, after) match {
          case (Some(b), Some(a)) if a == b =>
            driver.reporter.info(s"The plugin reports the DUT's clock ${b.toString.toLowerCase}")
          case (Some(b), Some(a)) =>
            driver.reporter.info(
              s"The plugin reports the DUT's clock ${b.toString.toLowerCase} before the events and ${a.toString.toLowerCase} after")
          case _ => ()
        }
        Nil
      }
    }

  // Double-bit Binary Inputs
  def generateDoubleBitBinaryInputPattern(): TestStep = action("Set the double-bit inputs to a known pattern") {
    (plugin, reporter) =>
      plugin.generatePattern(reporter, PointKind.DoubleBitInput)
  }

  def generateDoubleBitBinaryInputChangeEvents(): TestStep =
    binaryEvents("Change double-bit binary inputs several times, one or more of them, to create events") {
      (plugin, reporter) =>
        plugin.generateInputChanges(reporter, PointKind.DoubleBitInput)
    }

  def generateExactDoubleBitBinaryInputChangeEvents(numEvents: Int): TestStep =
    binaryEvents(s"Generate ${numEvents} Double-bit Binary Input Change Event(s)") { (plugin, reporter) =>
      plugin.generateExactInputEvents(reporter, PointKind.DoubleBitInput, numEvents)
    }

  // Counters
  def generateCounterInputData(): TestStep = action("Make every binary counter count up") { (plugin, reporter) =>
    plugin.generateInputChanges(reporter, PointKind.Counter)
  }

  def recordCurrentCounterValues(): TestStep = action("Note the counters' current values, for a later check") {
    (plugin, reporter) =>
      plugin.recordCurrentCounterValues(reporter)
  }

  def generateExactCounterChangeEvents(numEvents: Int): TestStep =
    action(s"Generate ${numEvents} Counter Change Event(s)") { (plugin, reporter) =>
      plugin.generateExactInputEvents(reporter, PointKind.Counter, numEvents)
    }

  // Analog Inputs
  def generateAnalogInputPattern(): TestStep = action("Input analog data") { (plugin, reporter) =>
    plugin.generatePattern(reporter, PointKind.AnalogInput)
  }

  def generateAnalogInputChangeEvents(): TestStep = action(
    "Raise an analog input by more than its configured deadband and hold it steady, so that an event is generated") {
    (plugin, reporter) =>
      plugin.generateInputChanges(reporter, PointKind.AnalogInput)
  }

  def generateExactAnalogInputChangeEvents(numEvents: Int): TestStep =
    action(s"Generate ${numEvents} Analog Input Change Event(s)") { (plugin, reporter) =>
      plugin.generateExactInputEvents(reporter, PointKind.AnalogInput, numEvents)
    }

  def generateExactSmallAnalogInputEvents(numEvents: Int): TestStep =
    action(s"Generate $numEvents analog input events, with values whose magnitudes are below 32768") {
      (plugin, reporter) =>
        plugin.generateExactSmallAnalogInputEvents(reporter, numEvents)
    }

  def generateExactFrozenCounterEvents(numEvents: Int): TestStep =
    action(s"Generate $numEvents frozen counter events") { (plugin, reporter) =>
      plugin.generateExactFrozenCounterEvents(reporter, numEvents)
    }

  def setFlagsNormal(kind: PointKind): TestStep =
    action(s"Set every ${kind.name} point's flags normal: ONLINE set, all others clear") { (plugin, reporter) =>
      plugin.setFlagsNormal(reporter, kind)
    }

  def setColdRestartSupport(enabled: Boolean): TestStep =
    action(s"${enable(enabled)} the DUT's support for cold restart") { (plugin, reporter) =>
      plugin.setColdRestartSupport(reporter, enabled)
    }

  // Unsolicited responses
  def enableUnsolicitedResponse(enabled: Boolean): TestStep =
    action(s"Configure the unsolicited response mode to '$enabled'") { (plugin, reporter) =>
      plugin.enableUnsolicitedResponse(reporter, enabled)
    }

  def setUnsolicitedResponseTimeout(timeoutMs: Int): TestStep =
    action(s"Set the unsolicited confirmation timeout to $timeoutMs milliseconds (to follow the test spec, prefer 5 seconds in the configuration)") {
      (plugin, reporter) =>
        plugin.setUnsolicitedResponseTimeout(reporter, timeoutMs)
    }

  def setMaxUnsolicitedRetries(numRetries: Option[Int]): TestStep = {
    val maxString = numRetries.fold("indefinite")(_.toString)
    action(s"Set the DUT's unsolicited retry limit to $maxString") { (plugin, reporter) =>
      plugin.setMaxUnsolicitedRetries(reporter, numRetries)
    }
  }

  def setMasterAddress(address: Int): TestStep =
    action(s"Configure the DUT to transmit unsolicited responses to master address $address") { (plugin, reporter) =>
      plugin.setMasterAddress(reporter, address)
    }

  // tells a manual tester about a timed step before its window opens
  def prepare(notice: String): TestStep = action(s"Tell a manual tester: $notice") { (plugin, reporter) =>
    plugin.prepare(reporter, notice)
  }

  def generateUnsolicitedEvents(): TestStep =
    action("Generate Class 1, 2 or 3 events that would normally trigger an unsolicited response") {
      (plugin, reporter) =>
        plugin.generateUnsolicitedEvents(reporter, ActionTiming.Now)
    }

  def generateNotEnoughUnsolicitedEvents(): TestStep =
    action("Create some class 1, 2 or 3 events, too few for the DUT to send an unsolicited response") {
      (plugin, reporter) =>
        plugin.generateNotEnoughUnsolicitedEvents(reporter)
    }

  // Misc
  def generateTimeEvent(): TestStep =
    action("Trigger an event of a supported timestamped type, recording when it occurs for the timestamp check") {
      (plugin, reporter) =>
        plugin.generateTimeEvent(reporter)
    }

  def enableRemoteSupervisoryControl(): TestStep =
    action("Set the DUT's remote supervisory control to 'Enabled' or 'Remote', globally and for every point") {
      (plugin, reporter) =>
        plugin.enableRemoteSupervisoryControl(reporter)
    }

  def setGlobalRemoteSupervisoryControl(): TestStep =
    action("Set the DUT's global remote supervisory switch to 'Disabled' or 'Local'") { (plugin, reporter) =>
      plugin.setGlobalRemoteSupervisoryControl(reporter)
    }

  def setIndividualRemoteSupervisoryControl(index: Int): TestStep =
    action(s"Put the output at index ${index} in local control (its remote control switch or setting to 'Disabled' or 'Local')") {
      (plugin, reporter) =>
        plugin.setIndividualRemoteSupervisoryControl(reporter, index)
    }

  def setMaxFragmentSize(maxFragmentSize: Int): TestStep =
    action(s"Configure the max fragment size to be no more than $maxFragmentSize") { (plugin, reporter) =>
      plugin.setMaxFragmentSize(reporter, maxFragmentSize)
    }

  def generateMultiFragmentResponse(): TestStep =
    action("Generate enough data for a response of more than one fragment") {
      (plugin, reporter) =>
        plugin.generateMultiFragmentResponse(reporter)
    }
}
