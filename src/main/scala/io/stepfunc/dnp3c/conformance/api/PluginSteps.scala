// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api
import io.stepfunc.dnp3c.dnp3.app.EventClass
import io.stepfunc.dnp3c.dnp3.{ ActionTiming, Comment, InputKind, IntegrationPlugin, PluginReporter, TestDriver }
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object PluginSteps {

  // a step that has the plugin act on the DUT
  private def action(desc: Comment*)(act: (IntegrationPlugin, PluginReporter) => Unit): TestStep =
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

  def uninstallBinaryOutputs: TestStep = action("Uninstall or disable every binary output point on the DUT") {
    (plugin, reporter) =>
      plugin.uninstallBinaryOutputs(reporter)
  }

  def checkBinaryOutputsOperate(indices: Iterable[Int]): PluginCheck =
    PluginCheck(indices.map(idx => Comment(s"Check that binary control point $idx operate")).toSeq*)(
      (plugin, reporter) => indices.toList.flatMap(idx => plugin.checkBinaryOutputOperate(reporter, idx)))

  def checkBinaryOutputOperate(index: Int): PluginCheck =
    PluginCheck(s"Check that binary control point $index operate")((plugin, reporter) =>
      plugin.checkBinaryOutputOperate(reporter, index))

  def checkBinaryOutputNotOperate(): PluginCheck =
    PluginCheck("Check that no binary control point operated")((plugin, reporter) =>
      plugin.checkBinaryOutputNotOperate(reporter))

  def reinstallPoints: TestStep =
    action("Reinstall or re-enable the points that were uninstalled or disabled, so later procedures can use them") {
      (plugin, reporter) =>
        plugin.reinstallPoints(reporter)
    }

  def uninstallAnalogOutputs: TestStep = action("Uninstall or disable every analog output point on the DUT") {
    (plugin, reporter) =>
      plugin.uninstallAnalogOutputs(reporter)
  }

  def checkAnalogOutputsOperate(indices: Iterable[Int]): PluginCheck =
    PluginCheck(indices.map(idx => Comment(s"Check that analog control point $idx operate")).toSeq*)(
      (plugin, reporter) => indices.toList.flatMap(idx => plugin.checkAnalogOutputOperate(reporter, idx)))

  def checkAnalogOutputOperate(index: Int): PluginCheck =
    PluginCheck(s"Check that analog control point $index operate")((plugin, reporter) =>
      plugin.checkAnalogOutputOperate(reporter, index))

  def checkAnalogOutputNotOperate(): PluginCheck =
    PluginCheck("Check that no analog control point operated")((plugin, reporter) =>
      plugin.checkAnalogOutputNotOperate(reporter))

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
  def uninstallBinaryInputs: TestStep = action("Uninstall or disable every binary input point on the DUT") {
    (plugin, reporter) =>
      plugin.uninstallBinaryInputs(reporter)
  }

  def generateBinaryInputPattern(): TestStep = action("Set the binary inputs to a known pattern") {
    (plugin, reporter) =>
      plugin.generateBinaryInputPattern(reporter)
  }

  def generateBinaryInputChangeEvents(): TestStep =
    action("Change binary inputs several times, one or more of them, to create events") {
      (plugin, reporter) =>
        plugin.generateInputChanges(reporter, InputKind.BinaryInput)
    }

  def generateExactBinaryInputChangeEvents(numEvents: Int): TestStep =
    action(s"Generate ${numEvents} Binary Input Change Event(s)") { (plugin, reporter) =>
      plugin.generateExactInputEvents(reporter, InputKind.BinaryInput, numEvents)
    }

  // Double-bit Binary Inputs
  def uninstallDoubleBitBinaryInputs: TestStep =
    action("Uninstall or disable every double-bit binary input point on the DUT") { (plugin, reporter) =>
      plugin.uninstallDoubleBitBinaryInputs(reporter)
    }

  def generateDoubleBitBinaryInputPattern(): TestStep = action("Set the double-bit inputs to a known pattern") {
    (plugin, reporter) =>
      plugin.generateDoubleBitBinaryInputPattern(reporter)
  }

  def generateDoubleBitBinaryInputChangeEvents(): TestStep =
    action("Change double-bit binary inputs several times, one or more of them, to create events") {
      (plugin, reporter) =>
        plugin.generateInputChanges(reporter, InputKind.DoubleBitInput)
    }

  def generateExactDoubleBitBinaryInputChangeEvents(numEvents: Int): TestStep =
    action(s"Generate ${numEvents} Double-bit Binary Input Change Event(s)") { (plugin, reporter) =>
      plugin.generateExactInputEvents(reporter, InputKind.DoubleBitInput, numEvents)
    }

  // Counters
  def uninstallFrozenCounters: TestStep = action("Uninstall or disable every frozen counter on the DUT") {
    (plugin, reporter) =>
      plugin.uninstallFrozenCounters(reporter)
  }

  def uninstallAnalogInputs: TestStep = action("Uninstall or disable every analog input on the DUT") {
    (plugin, reporter) =>
      plugin.uninstallAnalogInputs(reporter)
  }

  def uninstallCounterInputs: TestStep = action("Uninstall or disable every counter point on the DUT") {
    (plugin, reporter) =>
      plugin.uninstallCounters(reporter)
  }

  def generateCounterInputData(): TestStep = action("Make every binary counter count up") { (plugin, reporter) =>
    plugin.generateInputChanges(reporter, InputKind.Counter)
  }

  def recordCurrentCounterValues(): TestStep = action("Note the counters' current values, for a later check") {
    (plugin, reporter) =>
      plugin.recordCurrentCounterValues(reporter)
  }

  def generateExactCounterChangeEvents(numEvents: Int): TestStep =
    action(s"Generate ${numEvents} Counter Change Event(s)") { (plugin, reporter) =>
      plugin.generateExactInputEvents(reporter, InputKind.Counter, numEvents)
    }

  // Analog Inputs
  def generateAnalogInputPattern(): TestStep = action("Input analog data") { (plugin, reporter) =>
    plugin.generateAnalogInputPattern(reporter)
  }

  def generateAnalogInputChangeEvents(): TestStep = action(
    "Raise an analog input by more than its configured deadband and hold it steady, so that an event is generated") {
    (plugin, reporter) =>
      plugin.generateInputChanges(reporter, InputKind.AnalogInput)
  }

  def generateExactAnalogInputChangeEvents(numEvents: Int): TestStep =
    action(s"Generate ${numEvents} Analog Input Change Event(s)") { (plugin, reporter) =>
      plugin.generateExactInputEvents(reporter, InputKind.AnalogInput, numEvents)
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
