// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection, section }
import io.stepfunc.dnp3c.conformance.v27
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.common.SupervisoryControl.Numbering

/**
 * The sections and tests of the DNP3 IED Certification Procedure, Subset Levels 1, 2 and 3, revision 3.1r1, with
 * their titles and, where they differ from 1, 2 and 3, the subset levels they apply to.
 *
 * A test 3.1r1 numbers has its number, whether in a heading or on its "Test Procedure" line. One it doesn't has its
 * section's number followed by a letter, a for the section's first unnumbered test, b for the next and so on, e.g.
 * 8.6.5b; a letter can't be mistaken for one of 3.1r1's own numbers, and no section has more than 26 such tests.
 * Unsolicited and Polled Responses, one test in sections, has 8.11.2.5, which its numbers 8.11.2.5.1 to 8.11.2.5.9
 * give it. Material that isn't a test, such as desired behavior, terminology and setup, isn't in the tree. 3.1r1's 8.21
 * and 8.22 run the 8.13 and 8.14 tests with double-bit inputs: theirs are lettered in the same order, so 8.22c is
 * 8.14.2c's test, and their titles say which test each runs.
 *
 * A test whose steps are the same as a 2.7 test's, or differ only in wording, places that test's definition, which is
 * named after its 2.7 number, e.g. `v27.Procedure8_2_1_2_1`; 3.1r1's rules for every test apply to it as to any other.
 * Where only the step numbers differ, the definition takes 3.1r1's numbering (8.6.4).
 */
object Tree {

  private def test(
      id: String,
      title: String,
      definition: ProcedureDefinition,
      levels: Option[Set[Int]] = None): TestProcedure =
    TestProcedure(id, title, definition, levels)

  // the tests TB2017-001 gave 2.7, with 3.1r1's step numbers
  private val global8641 = new v27.GlobalRemoteSupervisoryControl(Numbering.dotted)
  private val individual8642 = new v27.IndividualRemoteSupervisoryControl(Numbering.afterCondition)

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
        test("6.1.2", "Test Procedure", v27.Procedure6_1_2)
      ),
      section("6.3", "Request Link Status")(
        test("6.3.2", "Test Procedure", v27.Procedure6_3_2)
      ),
      section("6.4", "Test Retries")(
        test("6.4.2", "Test Procedure", new v27.TestRetries(emptyStep14 = false))
      ),
      section("6.5", "DIR and FCV Bits")(
        test("6.5.2", "Test Procedure", v27.Procedure6_5_2)
      ),
      section("6.6", "Data Link Rejects Invalid Frames")(
        section("6.6.2", "Test Procedure – Primary Frames")(
          test("6.6.2a", "Invalid Start Octets", v27.Procedure6_6_2_1),
          test("6.6.2b", "Invalid Primary Function Code", v27.Procedure6_6_2_2),
          test("6.6.2c", "Invalid Destination Address", v27.Procedure6_6_2_3),
          test("6.6.2d", "No Inter-frame Gap", NoInterFrameGap.noInterFrameGap),
          test("6.6.2e", "Invalid CRC", v27.Procedure6_6_2_4),
          test("6.6.2f", "Invalid FCV", v27.Procedure6_6_2_5)
        ),
        section("6.6.3", "Test Procedure – Secondary Frames")(
          test("6.6.3a", "Invalid Start Octets", v27.Procedure6_6_3_1),
          test("6.6.3b", "Invalid Secondary Function Code", v27.Procedure6_6_3_2),
          test("6.6.3c", "Invalid Destination Address", v27.Procedure6_6_3_3),
          test("6.6.3d", "Invalid CRC", v27.Procedure6_6_3_4)
        )
      ),
      section("6.7", "Self-Address Support")(
        test("6.7.2", "Test Procedure", v27.Procedure6_7_2)
      )
    ),
    section("7", "Transport Layer")(
      test("7.2", "Test Procedure", v27.Procedure7_2)
    ),
    section("8", "Application Layer")(
      section("8.1", "Binary Output Status")(
        test("8.1.2", "Test Procedure", Definitions.binaryOutputStatus)
      ),
      section("8.2", "Binary Outputs")(
        section("8.2.1", "Select Before Operate")(
          test("8.2.1.2.1", "Binary Output, SBO, 0x28", v27.Procedure8_2_1_2_1),
          test("8.2.1.2.2", "Binary Output, SBO, 0x17", v27.Procedure8_2_1_2_2),
          test("8.2.1.2.3", "Binary Output, SBO, To Uninstalled Point", v27.Procedure8_2_1_2_3),
          test("8.2.1.2.4", "Binary Output, SBO, Execute Issued After Timeout", v27.Procedure8_2_1_2_4),
          test("8.2.1.2.5", "Binary Output, SBO, Execute to Different Point Than Select", v27.Procedure8_2_1_2_5),
          test(
            "8.2.1.2.6",
            "Binary Output, SBO, Execute On Time does not match Select On Time",
            v27.Procedure8_2_1_2_6),
          test(
            "8.2.1.2.7",
            "Binary Output, SBO, Execute Off Time does not match Select Off Time",
            v27.Procedure8_2_1_2_7),
          test(
            "8.2.1.2.8",
            "Binary Output, SBO, Select with supported Code, Execute with different Code",
            v27.Procedure8_2_1_2_8),
          test("8.2.1.2.9", "Binary Output, SBO, Select 0x28, Execute 0x17", v27.Procedure8_2_1_2_9),
          test("8.2.1.2.10", "Binary Output, SBO, Configurable Device", v27.Procedure8_2_1_2_10),
          test("8.2.1.2.11", "Binary Output, SBO, Same Sequence Number Select Retries", v27.Procedure8_2_1_2_11),
          test(
            "8.2.1.2.12",
            "Binary Output, SBO, Incrementing Sequence Number Select Retries",
            v27.Procedure8_2_1_2_12),
          test("8.2.1.2.13", "Binary Output, SBO, Same Sequence Number Operate Retries", v27.Procedure8_2_1_2_13),
          test(
            "8.2.1.2.14",
            "Binary Output, SBO, Incrementing Sequence Number Operate Retries",
            v27.Procedure8_2_1_2_14),
          test("8.2.1.2.15", "Binary Output, SBO, Sequence number checking", v27.Procedure8_2_1_2_15)
        ),
        section("8.2.2", "Direct Operate with Acknowledge")(
          test("8.2.2.2.1", "Binary Output, Direct Operate", v27.Procedure8_2_2_2_1),
          test("8.2.2.2.2", "Binary Output, Direct Operate to Uninstalled Point", v27.Procedure8_2_2_2_2),
          test("8.2.2.2.3", "Binary Output, Direct Operate, Configurable Device", v27.Procedure8_2_2_2_3)
        ),
        section("8.2.3", "Direct Operate, No Acknowledge")(
          test("8.2.3.2.1", "Binary Output, Direct Operate, No Acknowledge", v27.Procedure8_2_3_2_1),
          test(
            "8.2.3.2.2",
            "Binary Output, Direct Operate, No Acknowledge, to an Uninstalled Point",
            v27.Procedure8_2_3_2_2),
          test(
            "8.2.3.2.3",
            "Binary Output, Direct Operate, No Acknowledge, Configurable Device",
            v27.Procedure8_2_3_2_3)
        ),
        section("8.2.4", "Multiple Object Requests")(
          test("8.2.4a", "Test Procedure", v27.Procedure8_2_4_2)
        ),
        section("8.2.5", "Control Code Support")(
          test("8.2.5.2.1", "Binary Output, Complementary Function Support", v27.Procedure8_2_5_2_1),
          test("8.2.5.2.2", "Binary Output, Single Function Support", v27.Procedure8_2_5_2_2)
        ),
        section("8.2.6", "No Control When Status Code is Non-Zero")(
          test("8.2.6a", "Test Procedure", ControlStatus.nonZeroStatus)
        )
      ),
      section("8.3", "Analog Output Status")(
        test("8.3.2", "Test Procedure", Definitions.analogOutputStatus)
      ),
      section("8.4", "Analog Outputs")(
        section("8.4.1", "Select Before Operate")(
          test("8.4.1.2.1", "Analog Output, SBO, 0x28", v27.Procedure8_4_1_2_1),
          test("8.4.1.2.2", "Analog Output, SBO, 0x17", v27.Procedure8_4_1_2_2),
          test("8.4.1.2.3", "Analog Output, SBO, to Uninstalled Point", v27.Procedure8_4_1_2_3),
          test("8.4.1.2.4", "Analog Output, SBO, Execute Issued After Time-out", v27.Procedure8_4_1_2_4),
          test(
            "8.4.1.2.5",
            "Analog Output, SBO, Execute Value does not match Select Value",
            v27.Procedure8_4_1_2_5),
          test("8.4.1.2.6", "Analog Output, SBO, Select 0x28, Execute 0x17", v27.Procedure8_4_1_2_6),
          test("8.4.1.2.7", "Analog Output, SBO, Configurable Device", v27.Procedure8_4_1_2_7),
          test("8.4.1.2.8", "Analog Output, SBO, Same Sequence Number Select Retries", v27.Procedure8_4_1_2_8),
          test(
            "8.4.1.2.9",
            "Analog Output, SBO, Incrementing Sequence Number Select Retries",
            v27.Procedure8_4_1_2_9),
          test("8.4.1.2.10", "Analog Output, SBO, Same Sequence Number Operate Retries", v27.Procedure8_4_1_2_10),
          test(
            "8.4.1.2.11",
            "Analog Output, SBO, Incrementing Sequence Number Operate Retries",
            v27.Procedure8_4_1_2_11),
          test("8.4.1.2.12", "Analog Output, SBO, Sequence number checking", v27.Procedure8_4_1_2_12),
          test("8.4.1.2.13", "Analog Output, SBO, 32-bit values", AnalogOutput32.sbo)
        ),
        section("8.4.2", "Direct Operate with Acknowledge")(
          test("8.4.2.2.1", "Analog Output, Direct Operate", v27.Procedure8_4_2_2_1),
          test("8.4.2.2.2", "Analog Output, Direct Operate, to Uninstalled Point", v27.Procedure8_4_2_2_2),
          test("8.4.2.2.3", "Analog Output, Direct Operate, Configurable Device", v27.Procedure8_4_2_2_3)
        ),
        section("8.4.3", "Direct Operate, No Acknowledge")(
          test("8.4.3.2.1", "Analog Output, Direct Operate, No Acknowledge", v27.Procedure8_4_3_2_1),
          test(
            "8.4.3.2.2",
            "Analog Output, Direct Operate, No Acknowledge, to an Uninstalled Point",
            v27.Procedure8_4_3_2_2),
          test(
            "8.4.3.2.3",
            "Analog Output, Direct Operate, No Acknowledge, Configurable Device",
            v27.Procedure8_4_3_2_3)
        ),
        section("8.4.4", "Multiple Object Requests")(
          test("8.4.4a", "Test Procedure", v27.Procedure8_4_4_2)
        )
      ),
      section("8.5", "Class Data")(
        section("8.5.1", "Class 0")(
          test("8.5.1a", "Test Procedure", v27.Procedure8_5_1_2)
        ),
        section("8.5.2", "Class 1")(
          test("8.5.2.2.1", "Class 1 Data, 0x06", ClassData.class1All),
          test("8.5.2.2.2", "Class 1 Data, 0x07", ClassData.class1OneByteCount),
          test("8.5.2.2.3", "Class 1 Data, 0x08", ClassData.class1TwoByteCount),
          test("8.5.2.2.4", "Class 1 Data Without Confirm", v27.Procedure8_5_2_2_4)
        ),
        section("8.5.3", "Class 2")(
          test("8.5.3.2.1", "Class 2 Data, 0x06", ClassData.class2All),
          test("8.5.3.2.2", "Class 2 Data, 0x07", ClassData.class2OneByteCount),
          test("8.5.3.2.3", "Class 2 Data, 0x08", ClassData.class2TwoByteCount),
          test("8.5.3.2.4", "Class 2 Data Without Confirm", v27.Procedure8_5_3_2_4)
        ),
        section("8.5.4", "Class 3")(
          test("8.5.4.2.1", "Class 3 Data, 0x06", ClassData.class3All),
          test("8.5.4.2.2", "Class 3 Data, 0x07", ClassData.class3OneByteCount),
          test("8.5.4.2.3", "Class 3 Data, 0x08", ClassData.class3TwoByteCount),
          test("8.5.4.2.4", "Class 3 Data Without Confirm", v27.Procedure8_5_4_2_4)
        ),
        section("8.5.5", "Multiple Object Request")(
          test("8.5.5.2.1", "Multiple Object Request, Class 1, 2, and 3", v27.Procedure8_5_5_2_1),
          test("8.5.5.2.2", "Multiple Object Request, Class 1, 2, 3, and 0", v27.Procedure8_5_5_2_2)
        ),
        section("8.5.6", "Class Assignment Verification")(
          test("8.5.6a", "Test Procedure", ClassData.classAssignment)
        )
      ),
      section("8.6", "Indications")(
        section("8.6.1", "Restart")(
          test("8.6.1a", "Test Procedure", v27.Procedure8_6_1_2)
        ),
        section("8.6.2", "Bad Function")(
          test("8.6.2a", "Test Procedure", v27.Procedure8_6_2_2)
        ),
        section("8.6.3", "Object Unknown")(
          test("8.6.3a", "Test Procedure", v27.Procedure8_6_3_2)
        ),
        section("8.6.4", "Local")(
          test("8.6.4a", "Test Procedure for Devices with Global Remote Supervisory Control", global8641),
          test("8.6.4b", "Test Procedure for Devices with Individual Remote Supervisory Control", individual8642)
        ),
        section("8.6.5", "Broadcast Address and All Stations Indication")(
          test("8.6.5a", "Test Procedure", Broadcast.allStationsIndication),
          test("8.6.5b", "Test Procedure, Confirmed Response Options", Broadcast.confirmedResponseOptions)
        ),
        section("8.6.6", "Buffer Overflow")(
          test("8.6.6.2.1", "Buffer Overflow, Binary Input Change Event Buffers", v27.Procedure8_6_6_2_1),
          test("8.6.6.2.2", "Buffer Overflow, Analog Input Change Event Buffers", v27.Procedure8_6_6_2_2),
          test("8.6.6.2.3", "Buffer Overflow, Binary Counter Change Event Buffers", v27.Procedure8_6_6_2_3),
          test("8.6.6.2.4", "Buffer Overflow, Double-bit input Change Event Buffers", v27.Procedure8_6_6_2_4)
        )
      ),
      section("8.7", "Time")(
        section("8.7.1", "Non-LAN Time Sync Method")(
          test("8.7.1.1.2", "Delay Measurement", v27.Procedure8_7_1_2),
          test("8.7.1.2.2", "Synchronization", v27.Procedure8_7_2_2)
        ),
        section("8.7.2", "LAN Time Sync Method")(
          test("8.7.2a", "Test Procedure", LanTimeSync.lanTimeSync)
        )
      ),
      section("8.8", "Cold Restart")(
        test("8.8.2", "Test Procedure", new v27.ColdRestart(configures = true))
      ),
      section("8.9", "Application Layer Fragmentation")(
        section("8.9.1", "Use of FIR, FIN and SEQUENCE in Fragmentation")(
          test("8.9.1a", "Test Procedure", v27.Procedure8_9_1_2)
        ),
        section("8.9.2", "Use of Confirmation in Fragmentation")(
          test("8.9.2a", "Test Procedure", v27.Procedure8_9_2_2)
        )
      ),
      section("8.10", "Multi-Drop Support")(
        test("8.10.2", "Test Procedure", MultiDrop.multiDrop)
      ),
      section("8.11", "Unsolicited Responses")(
        section("8.11.2", "Test Procedure")(
          test("8.11.2a", "Unsolicited Response Configuration/Startup", v27.Procedure8_11_2_1),
          test("8.11.2b", "Unsolicited Response - Class 1 Data", v27.Procedure8_11_2_2),
          test("8.11.2c", "Unsolicited Response - Class 2 Data", v27.Procedure8_11_2_3),
          test("8.11.2d", "Unsolicited Response - Class 3 Data", v27.Procedure8_11_2_4),
          test("8.11.2.5", "Unsolicited and Polled Responses", Unsolicited.unsolicitedAndPolledResponses),
          test("8.11.2e", "Unsolicited Responses Off", v27.Procedure8_11_2_6)
        )
      ),
      section("8.12", "Collision Avoidance")(
        test("8.12.2", "Test Procedure", CollisionAvoidance.collisionAvoidance)
      ),
      section("8.13", "Binary Inputs Specific Object Requests")(
        section("8.13.2", "Test Procedure")(
          test(
            "8.13.2a",
            "Subset Level 1 device that does not support a read for binary input object group",
            StaticInputs.binaryLevelOne,
            levels = Some(Set(1))),
          test("8.13.2b", "Device does not have Binary Inputs", StaticInputs.binaryWithout),
          test("8.13.2c", "Device has Binary Inputs", StaticInputs.binaryHas),
          test("8.13.2d", "Device supports Binary Inputs, Configurable Device", StaticInputs.binaryConfigurable)
        )
      ),
      section("8.14", "Binary Input Change Specific Requests")(
        section("8.14.2", "Test Procedure")(
          test(
            "8.14.2a",
            "Subset Level 1 device that does not support a read for binary change events object group",
            InputEvents.binaryLevelOne,
            levels = Some(Set(1))
          ),
          test("8.14.2b", "Device does not support Binary Input Changes", InputEvents.binaryWithoutEvents),
          test("8.14.2c", "Binary Input Change, Qualifier 0x06", InputEvents.binaryAll),
          test("8.14.2d", "Binary Input Change, Qualifier 0x07", InputEvents.binaryCount07),
          test("8.14.2e", "Binary Input Change, Qualifier 0x08", InputEvents.binaryCount08),
          test("8.14.2f", "Binary Input Change Without Confirm", InputEvents.binaryNoConfirm),
          test("8.14.2g", "Binary Input Change Without Time, Qualifier 0x06", InputEvents.binaryAllWithoutTime),
          test("8.14.2h", "Binary Input Change Without Time, Qualifier 0x07", InputEvents.binaryCount07WithoutTime),
          test("8.14.2i", "Binary Input Change Without Time, Qualifier 0x08", InputEvents.binaryCount08WithoutTime),
          test("8.14.2j", "Binary Input Change with Time, Qualifier 0x06", InputEvents.binaryAllWithTime),
          test("8.14.2k", "Binary Input Change with Time, Qualifier 0x07", InputEvents.binaryCount07WithTime),
          test("8.14.2l", "Binary Input Change with Time, Qualifier 0x08", InputEvents.binaryCount08WithTime),
          test(
            "8.14.2m",
            "Binary Input Change with Relative Time, Qualifier 0x06",
            InputEvents.binaryAllWithRelativeTime),
          test(
            "8.14.2n",
            "Binary Input Change with Relative Time, Qualifier 0x07",
            InputEvents.binaryCount07WithRelativeTime),
          test(
            "8.14.2o",
            "Binary Input Change with Relative Time, Qualifier 0x08",
            InputEvents.binaryCount08WithRelativeTime),
          test("8.14.2p", "Binary Input Change with Relative Time, Long Interval", InputEvents.binaryLongInterval)
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
            "8.17.2a",
            "Subset Level 1 device that does not support a Read for Binary Counter Events",
            CounterAnalogEvents.counterLevelOne,
            levels = Some(Set(1))),
          test("8.17.2b", "Device does not support Binary Counter Events", CounterAnalogEvents.counterWithout),
          test("8.17.2c", "Binary Counters, Event, 0x06", CounterAnalogEvents.counterAll),
          test("8.17.2d", "Binary Counters, Event, 0x07", CounterAnalogEvents.counterOneByteCount),
          test("8.17.2e", "Binary Counters, Event, 0x08", CounterAnalogEvents.counterTwoByteCount),
          test("8.17.2f", "Binary Counters, Event Without Confirm", CounterAnalogEvents.counterWithoutConfirm)
        )
      ),
      section("8.18", "Analog Input")(
        section("8.18.2", "Test Procedure")(
          test(
            "8.18.2a",
            "Subset Level 1 Device that does not support a Read for Analog Inputs",
            StaticInputs.analogLevelOne,
            levels = Some(Set(1))),
          test("8.18.2b", "Device does not have Analog Inputs", StaticInputs.analogWithout),
          test("8.18.2c", "Device has Analog Inputs", StaticInputs.analogHas)
        )
      ),
      section("8.19", "Analog Change Event")(
        section("8.19.2", "Test Procedure")(
          test(
            "8.19.2a",
            "Subset Level 1 Device that does not support a Read for Analog Input Events",
            CounterAnalogEvents.analogLevelOne,
            levels = Some(Set(1))),
          test("8.19.2b", "Analog Input Change", CounterAnalogEvents.analogChange),
          test("8.19.2c", "Analog Input Change, Without Confirm", CounterAnalogEvents.analogWithoutConfirm),
          test("8.19.2.4", "Analog Input Change, Value Return", AnalogValueReturn.valueReturn)
        )
      ),
      section("8.20", "Multiple Read Requests")(
        section("8.20.2", "Test Procedure")(
          test(
            "8.20.2a",
            "Subset Level 1 Device that does not support a Read for Individual Object Types",
            MultipleReads.levelOne,
            levels = Some(Set(1))),
          test("8.20.2b", "Multiple Object Types", MultipleReads.multipleObjectTypes)
        )
      ),
      section("8.21", "Double-bit Inputs")(
        test("8.21.1", "Disable Double Bit Binaries", DisableDoubleBit.static),
        test(
          "8.21a",
          "Subset Level 1 device that does not support a read for the double-bit input object group (as 8.13.2a)",
          StaticInputs.doubleBitLevelOne,
          levels = Some(Set(1))
        ),
        test(
          "8.21b",
          "Device does not have Double-bit Inputs (as 8.13.2b)",
          StaticInputs.doubleBitWithout),
        test("8.21c", "Device has Double-bit Inputs (as 8.13.2c)", StaticInputs.doubleBitHas),
        test(
          "8.21d",
          "Device supports Double-bit Inputs, Configurable Device (as 8.13.2d)",
          StaticInputs.doubleBitConfigurable)
      ),
      section("8.22", "Double-bit Input Change")(
        test("8.22.1", "Disable Double Bit Binaries", DisableDoubleBit.events),
        test(
          "8.22a",
          "Subset Level 1 device that does not support a read for the double-bit change events object group (as 8.14.2a)",
          InputEvents.doubleBitLevelOne,
          levels = Some(Set(1))
        ),
        test(
          "8.22b",
          "Device does not support Double-bit Input Changes (as 8.14.2b)",
          InputEvents.doubleBitWithoutEvents),
        test("8.22c", "Double-bit Input Change, Qualifier 0x06 (as 8.14.2c)", InputEvents.doubleBitAll),
        test(
          "8.22d",
          "Double-bit Input Change, Qualifier 0x07 (as 8.14.2d)",
          InputEvents.doubleBitCount07),
        test(
          "8.22e",
          "Double-bit Input Change, Qualifier 0x08 (as 8.14.2e)",
          InputEvents.doubleBitCount08),
        test(
          "8.22f",
          "Double-bit Input Change Without Confirm (as 8.14.2f)",
          InputEvents.doubleBitNoConfirm),
        test(
          "8.22g",
          "Double-bit Input Change Without Time, Qualifier 0x06 (as 8.14.2g)",
          InputEvents.doubleBitAllWithoutTime),
        test(
          "8.22h",
          "Double-bit Input Change Without Time, Qualifier 0x07 (as 8.14.2h)",
          InputEvents.doubleBitCount07WithoutTime),
        test(
          "8.22i",
          "Double-bit Input Change Without Time, Qualifier 0x08 (as 8.14.2i)",
          InputEvents.doubleBitCount08WithoutTime),
        test(
          "8.22j",
          "Double-bit Input Change with Time, Qualifier 0x06 (as 8.14.2j)",
          InputEvents.doubleBitAllWithTime),
        test(
          "8.22k",
          "Double-bit Input Change with Time, Qualifier 0x07 (as 8.14.2k)",
          InputEvents.doubleBitCount07WithTime),
        test(
          "8.22l",
          "Double-bit Input Change with Time, Qualifier 0x08 (as 8.14.2l)",
          InputEvents.doubleBitCount08WithTime),
        test(
          "8.22m",
          "Double-bit Input Change with Relative Time, Qualifier 0x06 (as 8.14.2m)",
          InputEvents.doubleBitAllWithRelativeTime),
        test(
          "8.22n",
          "Double-bit Input Change with Relative Time, Qualifier 0x07 (as 8.14.2n)",
          InputEvents.doubleBitCount07WithRelativeTime),
        test(
          "8.22o",
          "Double-bit Input Change with Relative Time, Qualifier 0x08 (as 8.14.2o)",
          InputEvents.doubleBitCount08WithRelativeTime),
        test(
          "8.22p",
          "Double-bit Input Change with Relative Time, Long Interval (as 8.14.2p)",
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
          test("8.24.2a", "Binary Inputs", staticVariations.binaryInputs),
          test("8.24.2b", "Double-Bit Binary Inputs", staticVariations.doubleBitInputs),
          test("8.24.2c", "Binary Output Status", staticVariations.binaryOutputStatus),
          test("8.24.2d", "Counters", staticVariations.counters),
          test("8.24.2e", "Frozen Counters", staticVariations.frozenCounters),
          test("8.24.2f", "Analog Inputs", staticVariations.analogInputs),
          test("8.24.2g", "Analog Output Status", staticVariations.analogOutputStatus)
        )
      ),
      section("8.25", "Specific Variations for Events")(
        section("8.25.2", "Test Procedure")(
          test(
            "8.25.2a",
            "Binary Input Events",
            EventVariations.coveredBy("8.14, which reads binary input events in each variation"),
            levels = Some(Set(2, 3))),
          test(
            "8.25.2b",
            "Double-Bit Binary Input Events",
            EventVariations.coveredBy("8.22, which reads double-bit input events in each variation")),
          test("8.25.2c", "Counter Events", eventVariations.counters),
          test("8.25.2d", "Frozen Counters", eventVariations.frozenCounters),
          test("8.25.2e", "Analog Input Events", eventVariations.analogInputs)
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
          test("9.5.1.1.2", "Port 20000", Network.tcpPort20000),
          test("9.5.1.2.2", "Port Other Than 20000", Network.tcpOtherPort),
          test("9.5.1.3.2", "Specific IP Address", Network.tcpSpecificAddress),
          test("9.5.1.4.2", "Wildcard IP Address", Network.tcpWildcard),
          test("9.5.1.5.2", "UDP Unicast IP address with TCP as the Main Communication Method", Network.udpBroadcast),
          test("9.5.1.6.2", "UDP Multicast IP address with TCP as the Main Communication Method", Network.udpMulticast)
        ),
        section("9.5.2", "UDP Solicited")(
          test("9.5.2.1.2", "Port 20000", Network.udpPort20000),
          test("9.5.2.2.2", "Port Other Than 20000", Network.udpOtherPort),
          test("9.5.2.3.2", "Specific IP Address", Network.udpSpecificAddress),
          test("9.5.2.4.2", "Wildcard IP Address", Network.udpWildcard),
          test("9.5.2.5.2", "Responses to Port 20000", Network.udpResponsesTo20000)
        ),
        section("9.5.3", "UDP Unsolicited")(
          test("9.5.3.1.2", "Unsolicited Null 20000", Network.unsolicitedNull20000),
          test("9.5.3.2.2", "Unsolicited Null, Not 20000", Network.unsolicitedNullOtherPort),
          test("9.5.3.3.2", "Data Filled Messages to Port 20000", Network.unsolicitedData20000),
          test("9.5.3.4.2", "Data Filled Messages to Originating Port", Network.unsolicitedDataOriginatingPort),
          test("9.5.3.5.2", "Data Filled Messages to a Fixed Port Other Than 20000", Network.unsolicitedDataFixedPort)
        ),
        section("9.5.4", "Unsolicited with Both UDP and TCP")(
          test("9.5.4a", "Test Procedure", Network.unsolicitedUdpAndTcp)
        )
      )
    ),
    section("10", "Output Control Events")(
      section("10.3", "Binary")(
        section("10.3.2", "Binary Status in Group 0")(
          test("10.3.2a", "Test Procedure", OutputEvents.binaryStatusInClassZero)
        ),
        section("10.3.3", "Binary Output Status Events")(
          test("10.3.3a", "Test Procedure", OutputEvents.binaryStatusEvents)
        ),
        section("10.3.4", "Binary Command Events")(
          test("10.3.4a", "Test Procedure", OutputEvents.binaryCommandEvents)
        )
      ),
      section("10.4", "Analog")(
        section("10.4.2", "Analog Status in Group 0")(
          test("10.4.2a", "Test Procedure", OutputEvents.analogStatusInClassZero)
        ),
        section("10.4.3", "Analog Output Status Events")(
          test("10.4.3a", "Test Procedure", OutputEvents.analogStatusEvents)
        ),
        section("10.4.4", "Analog Command Events")(
          test("10.4.4a", "Test Procedure", OutputEvents.analogCommandEvents)
        )
      )
    )
  )
}
