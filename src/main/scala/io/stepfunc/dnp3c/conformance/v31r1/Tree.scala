// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api.{ TestElement, TestProcedure, TestSection, unimplemented }
import io.stepfunc.dnp3c.conformance.{ s6, s7, s8 }
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.s8.s86.s864.SupervisoryControl.Numbering

/**
 * The sections and tests of the DNP3 IED Certification Procedure, Subset Levels 1, 2 and 3, revision 3.1r1, with
 * their titles and, where they differ from 1, 2 and 3, the subset levels they apply to.
 *
 * A test 3.1r1 numbers has its number, whether in a heading or on its "Test Procedure" line. One it doesn't has its
 * section's number and its place among that section's unnumbered tests, e.g. 8.6.5-2, except for Unsolicited and
 * Polled Responses, one test in sections, whose numbers 8.11.2.5.1 to 8.11.2.5.9 give it 8.11.2.5. Material that isn't a test, such
 * as desired behavior, terminology and setup, isn't in the tree. 3.1r1's 8.21 and 8.22 run the 8.13 and 8.14 tests with double-bit inputs, so their
 * tests are named after those, e.g. 8.22-8.14.2-3.
 *
 * A test whose steps are the same as a 2.7 test's, or differ only in wording, places that test's definition; 3.1r1's
 * rules for every test apply to it as to any other. Where only the step numbers differ, the definition takes 3.1r1's
 * numbering (8.6.4). The rest aren't implemented yet.
 */
object Tree {

  private def section(id: String, title: String)(children: TestElement*): TestSection = {
    val (sectionId, sectionTitle) = (id, title)
    new TestSection {
      def id: String = sectionId
      def description: String = sectionTitle
      override def sections: Iterable[TestSection] = children.collect { case s: TestSection => s }
      override def procedures: Iterable[TestProcedure] = children.collect { case p: TestProcedure => p }
    }
  }

  private def test(
      id: String,
      title: String,
      definition: ProcedureDefinition,
      levels: Option[Set[Int]] = None): TestProcedure =
    TestProcedure(id, title, definition, levels)

  private def notYet(id: String, title: String, levels: Option[Set[Int]] = None): TestProcedure =
    unimplemented(id, title, levels)

  // the tests TB2017-001 gave 2.7, with 3.1r1's step numbers
  private val global8641 = new s8.s86.s864.GlobalRemoteSupervisoryControl(Numbering.dotted)
  private val individual8642 = new s8.s86.s864.IndividualRemoteSupervisoryControl(Numbering.afterCondition)

  // 8.23's three tests of each point type
  private final class RangeTests(t: PointRanges.RangedPoints) {
    val without: ProcedureDefinition = PointRanges.withoutPoints(t)
    val valid: ProcedureDefinition = PointRanges.validRequest(t)
    val outOfRange: ProcedureDefinition = PointRanges.outOfRange(t)
  }
  private object ranges {
    val binaryInputs = new RangeTests(PointRanges.binaryInputs)
    val binaryOutputStatus = new RangeTests(PointRanges.binaryOutputStatus)
    val counters = new RangeTests(PointRanges.counters)
    val frozenCounters = new RangeTests(PointRanges.frozenCounters)
    val analogInputs = new RangeTests(PointRanges.analogInputs)
    val analogOutputStatus = new RangeTests(PointRanges.analogOutputStatus)
  }

  // 8.24's test of each point type
  private object staticVariations {
    val binaryInputs: ProcedureDefinition = StaticVariations.test(StaticVariations.binaryInputs)
    val doubleBitInputs: ProcedureDefinition = StaticVariations.test(StaticVariations.doubleBitInputs)
    val binaryOutputStatus: ProcedureDefinition = StaticVariations.test(StaticVariations.binaryOutputStatus)
    val counters: ProcedureDefinition = StaticVariations.test(StaticVariations.counters)
    val frozenCounters: ProcedureDefinition = StaticVariations.test(StaticVariations.frozenCounters)
    val analogInputs: ProcedureDefinition = StaticVariations.test(StaticVariations.analogInputs)
    val analogOutputStatus: ProcedureDefinition = StaticVariations.test(StaticVariations.analogOutputStatus)
  }

  // 8.25's tests of counter, frozen counter and analog input events
  private object eventVariations {
    val counters: ProcedureDefinition = EventVariations.test(EventVariations.counters)
    val frozenCounters: ProcedureDefinition = EventVariations.test(EventVariations.frozenCounters)
    val analogInputs: ProcedureDefinition = EventVariations.test(EventVariations.analogInputs)
  }

  val sections: List[TestSection] = List(
    section("6", "Link Layer")(
      section("6.1", "Reset Link and Passive Confirm support")(
        test("6.1.2", "Test Procedure", s6.s61.s612.Procedure612)
      ),
      section("6.3", "Request Link Status")(
        test("6.3.2", "Test Procedure", s6.s63.Procedure632)
      ),
      section("6.4", "Test Retries")(
        test("6.4.2", "Test Procedure", new s6.s64.TestRetries(emptyStep14 = false))
      ),
      section("6.5", "DIR and FCV Bits")(
        test("6.5.2", "Test Procedure", s6.s65.Procedure652)
      ),
      section("6.6", "Data Link Rejects Invalid Frames")(
        section("6.6.2", "Test Procedure – Primary Frames")(
          test("6.6.2-1", "Invalid Start Octets", s6.s66.s662.Procedure6621),
          test("6.6.2-2", "Invalid Primary Function Code", s6.s66.s662.Procedure6622),
          test("6.6.2-3", "Invalid Destination Address", s6.s66.s662.Procedure6623),
          test("6.6.2-4", "No Inter-frame Gap", NoInterFrameGap.noInterFrameGap),
          test("6.6.2-5", "Invalid CRC", s6.s66.s662.Procedure6624),
          test("6.6.2-6", "Invalid FCV", s6.s66.s662.Procedure6625)
        ),
        section("6.6.3", "Test Procedure – Secondary Frames")(
          test("6.6.3-1", "Invalid Start Octets", s6.s66.s663.Procedure6631),
          test("6.6.3-2", "Invalid Secondary Function Code", s6.s66.s663.Procedure6632),
          test("6.6.3-3", "Invalid Destination Address", s6.s66.s663.Procedure6633),
          test("6.6.3-4", "Invalid CRC", s6.s66.s663.Procedure6634)
        )
      ),
      section("6.7", "Self-Address Support")(
        test("6.7.2", "Test Procedure", s6.s67.Procedure672)
      )
    ),
    section("7", "Transport Layer")(
      test("7.2", "Test Procedure", s7.Procedure72)
    ),
    section("8", "Application Layer")(
      section("8.1", "Binary Output Status")(
        test("8.1.2", "Test Procedure", Definitions.binaryOutputStatus)
      ),
      section("8.2", "Binary Outputs")(
        section("8.2.1", "Select Before Operate")(
          test("8.2.1.2.1", "Binary Output, SBO, 0x28", s8.s82.s821.Procedure82121),
          test("8.2.1.2.2", "Binary Output, SBO, 0x17", s8.s82.s821.Procedure82122),
          test("8.2.1.2.3", "Binary Output, SBO, To Uninstalled Point", s8.s82.s821.Procedure82123),
          test("8.2.1.2.4", "Binary Output, SBO, Execute Issued After Timeout", s8.s82.s821.Procedure82124),
          test("8.2.1.2.5", "Binary Output, SBO, Execute to Different Point Than Select", s8.s82.s821.Procedure82125),
          test(
            "8.2.1.2.6",
            "Binary Output, SBO, Execute On Time does not match Select On Time",
            s8.s82.s821.Procedure82126),
          test(
            "8.2.1.2.7",
            "Binary Output, SBO, Execute Off Time does not match Select Off Time",
            s8.s82.s821.Procedure82127),
          test(
            "8.2.1.2.8",
            "Binary Output, SBO, Select with supported Code, Execute with different Code",
            s8.s82.s821.Procedure82128),
          test("8.2.1.2.9", "Binary Output, SBO, Select 0x28, Execute 0x17", s8.s82.s821.Procedure82129),
          test("8.2.1.2.10", "Binary Output, SBO, Configurable Device", s8.s82.s821.Procedure821210),
          test("8.2.1.2.11", "Binary Output, SBO, Same Sequence Number Select Retries", s8.s82.s821.Procedure821211),
          test(
            "8.2.1.2.12",
            "Binary Output, SBO, Incrementing Sequence Number Select Retries",
            s8.s82.s821.Procedure821212),
          test("8.2.1.2.13", "Binary Output, SBO, Same Sequence Number Operate Retries", s8.s82.s821.Procedure821213),
          test(
            "8.2.1.2.14",
            "Binary Output, SBO, Incrementing Sequence Number Operate Retries",
            s8.s82.s821.Procedure821214),
          test("8.2.1.2.15", "Binary Output, SBO, Sequence number checking", s8.s82.s821.Procedure821215)
        ),
        section("8.2.2", "Direct Operate with Acknowledge")(
          test("8.2.2.2.1", "Binary Output, Direct Operate", s8.s82.s822.Procedure82221),
          test("8.2.2.2.2", "Binary Output, Direct Operate to Uninstalled Point", s8.s82.s822.Procedure82222),
          test("8.2.2.2.3", "Binary Output, Direct Operate, Configurable Device", s8.s82.s822.Procedure82223)
        ),
        section("8.2.3", "Direct Operate, No Acknowledge")(
          test("8.2.3.2.1", "Binary Output, Direct Operate, No Acknowledge", s8.s82.s823.Procedure82321),
          test(
            "8.2.3.2.2",
            "Binary Output, Direct Operate, No Acknowledge, to an Uninstalled Point",
            s8.s82.s823.Procedure82322),
          test(
            "8.2.3.2.3",
            "Binary Output, Direct Operate, No Acknowledge, Configurable Device",
            s8.s82.s823.Procedure82323)
        ),
        section("8.2.4", "Multiple Object Requests")(
          test("8.2.4-1", "Test Procedure", s8.s82.s824.Procedure8242)
        ),
        section("8.2.5", "Control Code Support")(
          test("8.2.5.2.1", "Binary Output, Complementary Function Support", s8.s82.s825.Procedure82521),
          test("8.2.5.2.2", "Binary Output, Single Function Support", s8.s82.s825.Procedure82522)
        ),
        section("8.2.6", "No Control When Status Code is Non-Zero")(
          test("8.2.6-1", "Test Procedure", ControlStatus.nonZeroStatus)
        )
      ),
      section("8.3", "Analog Output Status")(
        test("8.3.2", "Test Procedure", Definitions.analogOutputStatus)
      ),
      section("8.4", "Analog Outputs")(
        section("8.4.1", "Select Before Operate")(
          test("8.4.1.2.1", "Analog Output, SBO, 0x28", s8.s84.s841.Procedure84121),
          test("8.4.1.2.2", "Analog Output, SBO, 0x17", s8.s84.s841.Procedure84122),
          test("8.4.1.2.3", "Analog Output, SBO, to Uninstalled Point", s8.s84.s841.Procedure84123),
          test("8.4.1.2.4", "Analog Output, SBO, Execute Issued After Time-out", s8.s84.s841.Procedure84124),
          test(
            "8.4.1.2.5",
            "Analog Output, SBO, Execute Value does not match Select Value",
            s8.s84.s841.Procedure84125),
          test("8.4.1.2.6", "Analog Output, SBO, Select 0x28, Execute 0x17", s8.s84.s841.Procedure84126),
          test("8.4.1.2.7", "Analog Output, SBO, Configurable Device", s8.s84.s841.Procedure84127),
          test("8.4.1.2.8", "Analog Output, SBO, Same Sequence Number Select Retries", s8.s84.s841.Procedure84128),
          test(
            "8.4.1.2.9",
            "Analog Output, SBO, Incrementing Sequence Number Select Retries",
            s8.s84.s841.Procedure84129),
          test("8.4.1.2.10", "Analog Output, SBO, Same Sequence Number Operate Retries", s8.s84.s841.Procedure841210),
          test(
            "8.4.1.2.11",
            "Analog Output, SBO, Incrementing Sequence Number Operate Retries",
            s8.s84.s841.Procedure841211),
          test("8.4.1.2.12", "Analog Output, SBO, Sequence number checking", s8.s84.s841.Procedure841212),
          test("8.4.1.2.13", "Analog Output, SBO, 32-bit values", AnalogOutput32.sbo)
        ),
        section("8.4.2", "Direct Operate with Acknowledge")(
          test("8.4.2.2.1", "Analog Output, Direct Operate", s8.s84.s842.Procedure84221),
          test("8.4.2.2.2", "Analog Output, Direct Operate, to Uninstalled Point", s8.s84.s842.Procedure84222),
          test("8.4.2.2.3", "Analog Output, Direct Operate, Configurable Device", s8.s84.s842.Procedure84223)
        ),
        section("8.4.3", "Direct Operate, No Acknowledge")(
          test("8.4.3.2.1", "Analog Output, Direct Operate, No Acknowledge", s8.s84.s843.Procedure84321),
          test(
            "8.4.3.2.2",
            "Analog Output, Direct Operate, No Acknowledge, to an Uninstalled Point",
            s8.s84.s843.Procedure84322),
          test(
            "8.4.3.2.3",
            "Analog Output, Direct Operate, No Acknowledge, Configurable Device",
            s8.s84.s843.Procedure84323)
        ),
        section("8.4.4", "Multiple Object Requests")(
          test("8.4.4-1", "Test Procedure", s8.s84.s844.Procedure8442)
        )
      ),
      section("8.5", "Class Data")(
        section("8.5.1", "Class 0")(
          test("8.5.1-1", "Test Procedure", s8.s85.s851.Procedure8512)
        ),
        section("8.5.2", "Class 1")(
          test("8.5.2.2.1", "Class 1 Data, 0x06", ClassData.class1All),
          test("8.5.2.2.2", "Class 1 Data, 0x07", ClassData.class1OneByteCount),
          test("8.5.2.2.3", "Class 1 Data, 0x08", ClassData.class1TwoByteCount),
          test("8.5.2.2.4", "Class 1 Data Without Confirm", s8.s85.s852.Procedure85224)
        ),
        section("8.5.3", "Class 2")(
          test("8.5.3.2.1", "Class 2 Data, 0x06", ClassData.class2All),
          test("8.5.3.2.2", "Class 2 Data, 0x07", ClassData.class2OneByteCount),
          test("8.5.3.2.3", "Class 2 Data, 0x08", ClassData.class2TwoByteCount),
          test("8.5.3.2.4", "Class 2 Data Without Confirm", s8.s85.s853.Procedure85324)
        ),
        section("8.5.4", "Class 3")(
          test("8.5.4.2.1", "Class 3 Data, 0x06", ClassData.class3All),
          test("8.5.4.2.2", "Class 3 Data, 0x07", ClassData.class3OneByteCount),
          test("8.5.4.2.3", "Class 3 Data, 0x08", ClassData.class3TwoByteCount),
          test("8.5.4.2.4", "Class 3 Data Without Confirm", s8.s85.s854.Procedure85424)
        ),
        section("8.5.5", "Multiple Object Request")(
          test("8.5.5.2.1", "Multiple Object Request, Class 1, 2, and 3", s8.s85.s855.Procedure85521),
          test("8.5.5.2.2", "Multiple Object Request, Class 1, 2, 3, and 0", s8.s85.s855.Procedure85522)
        ),
        section("8.5.6", "Class Assignment Verification")(
          test("8.5.6-1", "Test Procedure", ClassData.classAssignment)
        )
      ),
      section("8.6", "Indications")(
        section("8.6.1", "Restart")(
          test("8.6.1-1", "Test Procedure", s8.s86.s861.Procedure8612)
        ),
        section("8.6.2", "Bad Function")(
          test("8.6.2-1", "Test Procedure", s8.s86.s862.Procedure8622)
        ),
        section("8.6.3", "Object Unknown")(
          test("8.6.3-1", "Test Procedure", s8.s86.s863.Procedure8632)
        ),
        section("8.6.4", "Local")(
          test("8.6.4-1", "Test Procedure for Devices with Global Remote Supervisory Control", global8641),
          test("8.6.4-2", "Test Procedure for Devices with Individual Remote Supervisory Control", individual8642)
        ),
        section("8.6.5", "Broadcast Address and All Stations Indication")(
          test("8.6.5-1", "Test Procedure", Broadcast.allStationsIndication),
          test("8.6.5-2", "Test Procedure, Confirmed Response Options", Broadcast.confirmedResponseOptions)
        ),
        section("8.6.6", "Buffer Overflow")(
          test("8.6.6.2.1", "Buffer Overflow, Binary Input Change Event Buffers", s8.s86.s866.Procedure86621),
          test("8.6.6.2.2", "Buffer Overflow, Analog Input Change Event Buffers", s8.s86.s866.Procedure86622),
          test("8.6.6.2.3", "Buffer Overflow, Binary Counter Change Event Buffers", s8.s86.s866.Procedure86623),
          test("8.6.6.2.4", "Buffer Overflow, Double-bit input Change Event Buffers", s8.s86.s866.Procedure86624)
        )
      ),
      section("8.7", "Time")(
        section("8.7.1", "Non-LAN Time Sync Method")(
          test("8.7.1.1.2", "Delay Measurement", s8.s87.s871.Procedure8712),
          test("8.7.1.2.2", "Synchronization", s8.s87.s872.Procedure8722)
        ),
        section("8.7.2", "LAN Time Sync Method")(
          test("8.7.2-1", "Test Procedure", LanTimeSync.lanTimeSync)
        )
      ),
      section("8.8", "Cold Restart")(
        test("8.8.2", "Test Procedure", new s8.s88.ColdRestart(configures = true))
      ),
      section("8.9", "Application Layer Fragmentation")(
        section("8.9.1", "Use of FIR, FIN and SEQUENCE in Fragmentation")(
          test("8.9.1-1", "Test Procedure", s8.s89.Procedure8912)
        ),
        section("8.9.2", "Use of Confirmation in Fragmentation")(
          test("8.9.2-1", "Test Procedure", s8.s89.Procedure8922)
        )
      ),
      section("8.10", "Multi-Drop Support")(
        notYet("8.10.2", "Test Procedure")
      ),
      section("8.11", "Unsolicited Responses")(
        section("8.11.2", "Test Procedure")(
          test("8.11.2-1", "Unsolicited Response Configuration/Startup", s8.s811.Procedure81121),
          test("8.11.2-2", "Unsolicited Response - Class 1 Data", s8.s811.Procedure81122),
          test("8.11.2-3", "Unsolicited Response - Class 2 Data", s8.s811.Procedure81123),
          test("8.11.2-4", "Unsolicited Response - Class 3 Data", s8.s811.Procedure81124),
          test("8.11.2.5", "Unsolicited and Polled Responses", Unsolicited.unsolicitedAndPolledResponses),
          test("8.11.2-5", "Unsolicited Responses Off", s8.s811.Procedure81126)
        )
      ),
      section("8.12", "Collision Avoidance")(
        notYet("8.12.2", "Test Procedure")
      ),
      section("8.13", "Binary Inputs Specific Object Requests")(
        section("8.13.2", "Test Procedure")(
          test(
            "8.13.2-1",
            "Subset Level 1 device that does not support a read for binary input object group",
            StaticInputs.binaryLevelOne,
            levels = Some(Set(1))),
          test("8.13.2-2", "Device does not have Binary Inputs", StaticInputs.binaryWithout),
          test("8.13.2-3", "Device has Binary Inputs", StaticInputs.binaryHas),
          test("8.13.2-4", "Device supports Binary Inputs, Configurable Device", StaticInputs.binaryConfigurable)
        )
      ),
      section("8.14", "Binary Input Change Specific Requests")(
        section("8.14.2", "Test Procedure")(
          test(
            "8.14.2-1",
            "Subset Level 1 device that does not support a read for binary change events object group",
            InputEvents.binaryLevelOne,
            levels = Some(Set(1))
          ),
          test("8.14.2-2", "Device does not support Binary Input Changes", InputEvents.binaryWithoutEvents),
          test("8.14.2-3", "Binary Input Change, Qualifier 0x06", InputEvents.binaryAll),
          test("8.14.2-4", "Binary Input Change, Qualifier 0x07", InputEvents.binaryCount07),
          test("8.14.2-5", "Binary Input Change, Qualifier 0x08", InputEvents.binaryCount08),
          test("8.14.2-6", "Binary Input Change Without Confirm", InputEvents.binaryNoConfirm),
          test("8.14.2-7", "Binary Input Change Without Time, Qualifier 0x06", InputEvents.binaryAllWithoutTime),
          test("8.14.2-8", "Binary Input Change Without Time, Qualifier 0x07", InputEvents.binaryCount07WithoutTime),
          test("8.14.2-9", "Binary Input Change Without Time, Qualifier 0x08", InputEvents.binaryCount08WithoutTime),
          test("8.14.2-10", "Binary Input Change with Time, Qualifier 0x06", InputEvents.binaryAllWithTime),
          test("8.14.2-11", "Binary Input Change with Time, Qualifier 0x07", InputEvents.binaryCount07WithTime),
          test("8.14.2-12", "Binary Input Change with Time, Qualifier 0x08", InputEvents.binaryCount08WithTime),
          test(
            "8.14.2-13",
            "Binary Input Change with Relative Time, Qualifier 0x06",
            InputEvents.binaryAllWithRelativeTime),
          test(
            "8.14.2-14",
            "Binary Input Change with Relative Time, Qualifier 0x07",
            InputEvents.binaryCount07WithRelativeTime),
          test(
            "8.14.2-15",
            "Binary Input Change with Relative Time, Qualifier 0x08",
            InputEvents.binaryCount08WithRelativeTime),
          test("8.14.2-16", "Binary Input Change with Relative Time, Long Interval", InputEvents.binaryLongInterval)
        )
      ),
      section("8.15", "Common Time of Occurrence")(
        test(
          "8.15.2",
          "Read for relative time binary change events; Relative time not supported",
          CommonTime.readWithRelativeTime),
        test("8.15.3", "Read for Relative Time; Relative time supported", CommonTime.relativeTimeSupported)
      ),
      section("8.16", "Binary Counters Specific Object Requests")(
        section("8.16.1", "Binary Counters, Running")(
          test(
            "8.16.1.2.1",
            "Subset Level 1 device that does not support a read for binary counters",
            StaticInputs.counterLevelOne,
            levels = Some(Set(1))),
          test("8.16.1.2.2", "Device does not have Binary Counters", StaticInputs.counterWithout),
          test("8.16.1.2.3", "Device responds with Binary Counters", StaticInputs.counterHas),
          test(
            "8.16.1.2.4",
            "Device supports Binary Counters, Configurable Device",
            StaticInputs.counterConfigurable)
        ),
        section("8.16.2", "Binary Counters, Frozen")(
          test(
            "8.16.2.2.1",
            "Subset Level 1 device that does not support a read for frozen counters",
            StaticInputs.frozenCounterLevelOne,
            levels = Some(Set(1))),
          test("8.16.2.2.2", "Device does not have Frozen Counters", StaticInputs.frozenCounterWithout),
          test("8.16.2.2.3", "Binary Counter, Freeze", FrozenCounters.freeze),
          test("8.16.2.2.4", "Binary Counter, Freeze and Clear", FrozenCounters.freezeAndClear),
          test("8.16.2.2.5", "Binary Counter, Freeze, No Acknowledge", FrozenCounters.freezeNoAck),
          test(
            "8.16.2.2.6",
            "Binary Counter, Freeze and Clear, No Acknowledge",
            FrozenCounters.freezeAndClearNoAck)
        )
      ),
      section("8.17", "Binary Counters, Event")(
        section("8.17.2", "Test Procedure")(
          test(
            "8.17.2-1",
            "Subset Level 1 device that does not support a Read for Binary Counter Events",
            CounterAnalogEvents.counterLevelOne,
            levels = Some(Set(1))),
          test("8.17.2-2", "Device does not support Binary Counter Events", CounterAnalogEvents.counterWithout),
          test("8.17.2-3", "Binary Counters, Event, 0x06", CounterAnalogEvents.counterAll),
          test("8.17.2-4", "Binary Counters, Event, 0x07", CounterAnalogEvents.counterOneByteCount),
          test("8.17.2-5", "Binary Counters, Event, 0x08", CounterAnalogEvents.counterTwoByteCount),
          test("8.17.2-6", "Binary Counters, Event Without Confirm", CounterAnalogEvents.counterWithoutConfirm)
        )
      ),
      section("8.18", "Analog Input")(
        section("8.18.2", "Test Procedure")(
          test(
            "8.18.2-1",
            "Subset Level 1 Device that does not support a Read for Analog Inputs",
            StaticInputs.analogLevelOne,
            levels = Some(Set(1))),
          test("8.18.2-2", "Device does not have Analog Inputs", StaticInputs.analogWithout),
          test("8.18.2-3", "Device has Analog Inputs", StaticInputs.analogHas)
        )
      ),
      section("8.19", "Analog Change Event")(
        section("8.19.2", "Test Procedure")(
          test(
            "8.19.2-1",
            "Subset Level 1 Device that does not support a Read for Analog Input Events",
            CounterAnalogEvents.analogLevelOne,
            levels = Some(Set(1))),
          test("8.19.2-2", "Analog Input Change", CounterAnalogEvents.analogChange),
          test("8.19.2-3", "Analog Input Change, Without Confirm", CounterAnalogEvents.analogWithoutConfirm),
          test("8.19.2.4", "Analog Input Change, Value Return", AnalogValueReturn.valueReturn)
        )
      ),
      section("8.20", "Multiple Read Requests")(
        section("8.20.2", "Test Procedure")(
          test(
            "8.20.2-1",
            "Subset Level 1 Device that does not support a Read for Individual Object Types",
            MultipleReads.levelOne,
            levels = Some(Set(1))),
          test("8.20.2-2", "Multiple Object Types", MultipleReads.multipleObjectTypes)
        )
      ),
      section("8.21", "Double-bit Inputs")(
        test("8.21.1", "Disable Double Bit Binaries", DisableDoubleBit.static),
        test(
          "8.21-8.13.2-1",
          "Subset Level 1 device that does not support a read for binary input object group, with double-bit inputs",
          StaticInputs.doubleBitLevelOne,
          levels = Some(Set(1))
        ),
        test(
          "8.21-8.13.2-2",
          "Device does not have Binary Inputs, with double-bit inputs",
          StaticInputs.doubleBitWithout),
        test("8.21-8.13.2-3", "Device has Binary Inputs, with double-bit inputs", StaticInputs.doubleBitHas),
        test(
          "8.21-8.13.2-4",
          "Device supports Binary Inputs, Configurable Device, with double-bit inputs",
          StaticInputs.doubleBitConfigurable)
      ),
      section("8.22", "Double-bit Input Change")(
        test("8.22.1", "Disable Double Bit Binaries", DisableDoubleBit.events),
        test(
          "8.22-8.14.2-1",
          "Subset Level 1 device that does not support a read for binary change events object group, with double-bit inputs",
          InputEvents.doubleBitLevelOne,
          levels = Some(Set(1))
        ),
        test(
          "8.22-8.14.2-2",
          "Device does not support Binary Input Changes, with double-bit inputs",
          InputEvents.doubleBitWithoutEvents),
        test("8.22-8.14.2-3", "Binary Input Change, Qualifier 0x06, with double-bit inputs", InputEvents.doubleBitAll),
        test(
          "8.22-8.14.2-4",
          "Binary Input Change, Qualifier 0x07, with double-bit inputs",
          InputEvents.doubleBitCount07),
        test(
          "8.22-8.14.2-5",
          "Binary Input Change, Qualifier 0x08, with double-bit inputs",
          InputEvents.doubleBitCount08),
        test(
          "8.22-8.14.2-6",
          "Binary Input Change Without Confirm, with double-bit inputs",
          InputEvents.doubleBitNoConfirm),
        test(
          "8.22-8.14.2-7",
          "Binary Input Change Without Time, Qualifier 0x06, with double-bit inputs",
          InputEvents.doubleBitAllWithoutTime),
        test(
          "8.22-8.14.2-8",
          "Binary Input Change Without Time, Qualifier 0x07, with double-bit inputs",
          InputEvents.doubleBitCount07WithoutTime),
        test(
          "8.22-8.14.2-9",
          "Binary Input Change Without Time, Qualifier 0x08, with double-bit inputs",
          InputEvents.doubleBitCount08WithoutTime),
        test(
          "8.22-8.14.2-10",
          "Binary Input Change with Time, Qualifier 0x06, with double-bit inputs",
          InputEvents.doubleBitAllWithTime),
        test(
          "8.22-8.14.2-11",
          "Binary Input Change with Time, Qualifier 0x07, with double-bit inputs",
          InputEvents.doubleBitCount07WithTime),
        test(
          "8.22-8.14.2-12",
          "Binary Input Change with Time, Qualifier 0x08, with double-bit inputs",
          InputEvents.doubleBitCount08WithTime),
        test(
          "8.22-8.14.2-13",
          "Binary Input Change with Relative Time, Qualifier 0x06, with double-bit inputs",
          InputEvents.doubleBitAllWithRelativeTime),
        test(
          "8.22-8.14.2-14",
          "Binary Input Change with Relative Time, Qualifier 0x07, with double-bit inputs",
          InputEvents.doubleBitCount07WithRelativeTime),
        test(
          "8.22-8.14.2-15",
          "Binary Input Change with Relative Time, Qualifier 0x08, with double-bit inputs",
          InputEvents.doubleBitCount08WithRelativeTime),
        test(
          "8.22-8.14.2-16",
          "Binary Input Change with Relative Time, Long Interval, with double-bit inputs",
          InputEvents.doubleBitLongInterval)
      ),
      section("8.23", "Specific Point Ranges")(
        section("8.23.2", "Test Procedure")(
          section("8.23.2.1", "Binary Inputs")(
            test("8.23.2.1.1", "Device Does Not Support Binary inputs", ranges.binaryInputs.without),
            test("8.23.2.1.2", "Device Support Binary inputs, Valid Request", ranges.binaryInputs.valid),
            test("8.23.2.1.3", "Device Supports Binary inputs, Out of Range Request", ranges.binaryInputs.outOfRange)
          ),
          section("8.23.2.2", "Binary Output Status")(
            test(
              "8.23.2.2.1",
              "Device Does Not Support Binary Output Status Points",
              ranges.binaryOutputStatus.without),
            test(
              "8.23.2.2.2",
              "Device Support Binary Output Status Points, Valid Request",
              ranges.binaryOutputStatus.valid),
            test(
              "8.23.2.2.3",
              "Device Supports Binary Output Status, Out of Range Request",
              ranges.binaryOutputStatus.outOfRange)
          ),
          section("8.23.2.3", "Counters")(
            test("8.23.2.3.1", "Device Does Not Support Counters", ranges.counters.without),
            test("8.23.2.3.2", "Device Support Counters, Valid Request", ranges.counters.valid),
            test("8.23.2.3.3", "Device Supports Counters, Out of Range Request", ranges.counters.outOfRange)
          ),
          section("8.23.2.4", "Frozen Counters")(
            test("8.23.2.4.1", "Device Does Not Support Frozen Counters", ranges.frozenCounters.without),
            test("8.23.2.4.2", "Device Support Frozen Counters, Valid Request", ranges.frozenCounters.valid),
            test(
              "8.23.2.4.3",
              "Device Supports Frozen Counters, Out of Range Request",
              ranges.frozenCounters.outOfRange)
          ),
          section("8.23.2.5", "Analog Inputs")(
            test("8.23.2.5.1", "Device Does Not Support Analog inputs", ranges.analogInputs.without),
            test("8.23.2.5.2", "Device Support Analog inputs, Valid Request", ranges.analogInputs.valid),
            test("8.23.2.5.3", "Device Supports Analog inputs, Out of Range Request", ranges.analogInputs.outOfRange)
          ),
          section("8.23.2.6", "Analog Output Status")(
            test("8.23.2.6.1", "Device Does Not Support Analog Output Status", ranges.analogOutputStatus.without),
            test("8.23.2.6.2", "Device Support Analog Output Status, Valid Request", ranges.analogOutputStatus.valid),
            test(
              "8.23.2.6.3",
              "Device Supports Analog Output Status, Out of Range Request",
              ranges.analogOutputStatus.outOfRange)
          )
        )
      ),
      section("8.24", "Specific variations for Static Inputs")(
        section("8.24.2", "Test Procedure")(
          test("8.24.2-1", "Binary Inputs", staticVariations.binaryInputs),
          test("8.24.2-2", "Double-Bit Binary Inputs", staticVariations.doubleBitInputs),
          test("8.24.2-3", "Binary Output Status", staticVariations.binaryOutputStatus),
          test("8.24.2-4", "Counters", staticVariations.counters),
          test("8.24.2-5", "Frozen Counters", staticVariations.frozenCounters),
          test("8.24.2-6", "Analog Inputs", staticVariations.analogInputs),
          test("8.24.2-7", "Analog Output Status", staticVariations.analogOutputStatus)
        )
      ),
      section("8.25", "Specific Variations for Events")(
        section("8.25.2", "Test Procedure")(
          test(
            "8.25.2-1",
            "Binary Input Events",
            EventVariations.coveredBy("8.14, which reads binary input events in each variation"),
            levels = Some(Set(2, 3))),
          test(
            "8.25.2-2",
            "Double-Bit Binary Input Events",
            EventVariations.coveredBy("8.22, which reads double-bit input events in each variation")),
          test("8.25.2-3", "Counter Events", eventVariations.counters),
          test("8.25.2-4", "Frozen Counters", eventVariations.frozenCounters),
          test("8.25.2-5", "Analog Input Events", eventVariations.analogInputs)
        )
      ),
      section("8.26", "Assign Class")(
        test("8.26.2", "Test Procedures", AssignClass.assignClass),
        test("8.26.3", "No-Class Assignment Test Procedures", AssignClass.noClass, levels = Some(Set(3)))
      )
    ),
    section("9", "Network")(
      section("9.5", "All Communication Methods")(
        section("9.5.1", "TCP Without DNP Unsolicited")(
          notYet("9.5.1.1.2", "Port 20000"),
          notYet("9.5.1.2.2", "Port Other Than 20000"),
          notYet("9.5.1.3.2", "Specific IP Address"),
          notYet("9.5.1.4.2", "Wildcard IP Address"),
          notYet("9.5.1.5.2", "UDP Unicast IP address with TCP as the Main Communication Method"),
          notYet("9.5.1.6.2", "UDP Multicast IP address with TCP as the Main Communication Method")
        ),
        section("9.5.2", "UDP Solicited")(
          notYet("9.5.2.1.2", "Port 20000"),
          notYet("9.5.2.2.2", "Port Other Than 20000"),
          notYet("9.5.2.3.2", "Specific IP Address"),
          notYet("9.5.2.4.2", "Wildcard IP Address"),
          notYet("9.5.2.5.2", "Responses to Port 20000")
        ),
        section("9.5.3", "UDP Unsolicited")(
          notYet("9.5.3.1.2", "Unsolicited Null 20000"),
          notYet("9.5.3.2.2", "Unsolicited Null, Not 20000"),
          notYet("9.5.3.3.2", "Data Filled Messages to Port 20000"),
          notYet("9.5.3.4.2", "Data Filled Messages to Originating Port"),
          notYet("9.5.3.5.2", "Data Filled Messages to a Fixed Port Other Than 20000")
        ),
        section("9.5.4", "Unsolicited with Both UDP and TCP")(
          notYet("9.5.4-1", "Test Procedure")
        )
      )
    ),
    section("10", "Output Control Events")(
      section("10.3", "Binary")(
        section("10.3.2", "Binary Status in Group 0")(
          test("10.3.2-1", "Test Procedure", OutputEvents.binaryStatusInClassZero)
        ),
        section("10.3.3", "Binary Output Status Events")(
          test("10.3.3-1", "Test Procedure", OutputEvents.binaryStatusEvents)
        ),
        section("10.3.4", "Binary Command Events")(
          test("10.3.4-1", "Test Procedure", OutputEvents.binaryCommandEvents)
        )
      ),
      section("10.4", "Analog")(
        section("10.4.2", "Analog Status in Group 0")(
          test("10.4.2-1", "Test Procedure", OutputEvents.analogStatusInClassZero)
        ),
        section("10.4.3", "Analog Output Status Events")(
          test("10.4.3-1", "Test Procedure", OutputEvents.analogStatusEvents)
        ),
        section("10.4.4", "Analog Command Events")(
          test("10.4.4-1", "Test Procedure", OutputEvents.analogCommandEvents)
        )
      )
    )
  )
}
