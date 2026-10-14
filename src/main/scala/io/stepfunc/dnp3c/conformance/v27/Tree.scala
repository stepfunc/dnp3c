// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection, section, unimplemented }

/**
 * The sections and tests of the DNP3 IED Certification Procedure, Subset Level 2, version 2.7, with their titles. Each
 * test's definition is named after its number, e.g. 8.2.1.2.1's is [[Procedure8_2_1_2_1]].
 */
object Tree {

  val sections: List[TestSection] = List(
    section("6", "Link Layer")(
      section("6.1", "Reset link and passive confirm support")(
        TestProcedure("6.1.2", "Test Procedure", Procedure6_1_2)
      ),
      section("6.3", "Request Link Status")(
        TestProcedure("6.3.2", "Test Procedure", Procedure6_3_2)
      ),
      section("6.4", "Test Retries")(
        TestProcedure("6.4.2", "Test Procedure", Procedure6_4_2)
      ),
      section("6.5", "DIR and FCV Bits")(
        TestProcedure("6.5.2", "Test Procedure", Procedure6_5_2)
      ),
      section("6.6", "Data Link Rejects Invalid Frames")(
        section("6.6.2", "Primary Frames")(
          TestProcedure("6.6.2.1", "Invalid Start Octets", Procedure6_6_2_1),
          TestProcedure("6.6.2.2", "Invalid Primary Function Code", Procedure6_6_2_2),
          TestProcedure("6.6.2.3", "Invalid Destination Address", Procedure6_6_2_3),
          TestProcedure("6.6.2.4", "Invalid CRC", Procedure6_6_2_4),
          TestProcedure("6.6.2.5", "Invalid FCV", Procedure6_6_2_5)
        ),
        section("6.6.3", "Secondary Frames")(
          TestProcedure("6.6.3.1", "Invalid Start Octets", Procedure6_6_3_1),
          TestProcedure("6.6.3.2", "Invalid Secondary Function Code", Procedure6_6_3_2),
          TestProcedure("6.6.3.3", "Invalid Destination Address", Procedure6_6_3_3),
          TestProcedure("6.6.3.4", "Invalid CRC", Procedure6_6_3_4)
        )
      ),
      section("6.7", "Self-Address Support")(
        TestProcedure("6.7.2", "Test Procedure", Procedure6_7_2)
      )
    ),
    section("7", "Transport Layer")(
      TestProcedure("7.2", "Segmentation of application layer class 0 poll", Procedure7_2)
    ),
    section("8", "Application Layer")(
      section("8.1", "Binary Output Status")(
        TestProcedure("8.1.2", "Test Procedure", Procedure8_1_2)
      ),
      section("8.2", "Binary Output Controls")(
        section("8.2.1", "Select Before Operate")(
          TestProcedure("8.2.1.2.1", "Binary Output, SBO, 0x28", Procedure8_2_1_2_1),
          TestProcedure("8.2.1.2.2", "Binary Output, SBO, 0x17", Procedure8_2_1_2_2),
          TestProcedure("8.2.1.2.3", "Binary Output, SBO, To Uninstalled Point", Procedure8_2_1_2_3),
          TestProcedure("8.2.1.2.4", "Binary Output, SBO, Execute Issued After Timeout", Procedure8_2_1_2_4),
          TestProcedure("8.2.1.2.5", "Binary Output, SBO, Execute to Different Point Than Select", Procedure8_2_1_2_5),
          TestProcedure(
            "8.2.1.2.6",
            "Binary Output, SBO, Execute On Time does not match Select On Time",
            Procedure8_2_1_2_6),
          TestProcedure(
            "8.2.1.2.7",
            "Binary Output, SBO, Execute Off Time does not match Select Off Time",
            Procedure8_2_1_2_7),
          TestProcedure(
            "8.2.1.2.8",
            "Binary Output, SBO, Select with supported Code, Execute with different Code",
            Procedure8_2_1_2_8),
          TestProcedure("8.2.1.2.9", "Binary Output, SBO, Select 0x28, Execute 0x17", Procedure8_2_1_2_9),
          TestProcedure("8.2.1.2.10", "Binary Output, SBO, Configurable Device", Procedure8_2_1_2_10),
          TestProcedure("8.2.1.2.11", "Binary Output, SBO, Same Sequence Number Select Retries", Procedure8_2_1_2_11),
          TestProcedure(
            "8.2.1.2.12",
            "Binary Output, SBO, Incrementing Sequence Number Select Retries",
            Procedure8_2_1_2_12),
          TestProcedure("8.2.1.2.13", "Binary Output, SBO, Same Sequence Number Operate Retries", Procedure8_2_1_2_13),
          TestProcedure(
            "8.2.1.2.14",
            "Binary Output, SBO, Incrementing Sequence Number Operate Retries",
            Procedure8_2_1_2_14),
          TestProcedure("8.2.1.2.15", "Binary Output, SBO, Sequence number checking", Procedure8_2_1_2_15)
        ),
        section("8.2.2", "Direct Operate with Acknowledge")(
          TestProcedure("8.2.2.2.1", "Binary Output, Direct Operate", Procedure8_2_2_2_1),
          TestProcedure("8.2.2.2.2", "Binary Output, Direct Operate to Uninstalled Point", Procedure8_2_2_2_2),
          TestProcedure("8.2.2.2.3", "Binary Output, Direct Operate, Configurable Device", Procedure8_2_2_2_3)
        ),
        section("8.2.3", "Direct Operate, No Acknowledge")(
          TestProcedure("8.2.3.2.1", "Binary Output, Direct Operate, No Acknowledge", Procedure8_2_3_2_1),
          TestProcedure(
            "8.2.3.2.2",
            "Binary Output, Direct Operate, No Acknowledge, to an Uninstalled Point",
            Procedure8_2_3_2_2),
          TestProcedure(
            "8.2.3.2.3",
            "Binary Output, Direct Operate, No Acknowledge, Configurable Device",
            Procedure8_2_3_2_3)
        ),
        section("8.2.4", "Multiple Object Requests")(
          TestProcedure("8.2.4.2", "Test Procedure", Procedure8_2_4_2)
        ),
        section("8.2.5", "Control Code Support")(
          TestProcedure("8.2.5.2.1", "Binary Output, Complementary Function Support", Procedure8_2_5_2_1),
          TestProcedure("8.2.5.2.2", "Binary Output, Single Function Support", Procedure8_2_5_2_2)
        )
      ),
      section("8.3", "Analog Output Status")(
        TestProcedure("8.3.2", "Test Procedure", Procedure8_3_2)
      ),
      section("8.4", "Analog Output Controls")(
        section("8.4.1", "Analog Output Controls, Select Before Operate")(
          TestProcedure("8.4.1.2.1", "Analog Output, SBO, 0x28", Procedure8_4_1_2_1),
          TestProcedure("8.4.1.2.2", "Analog Output, SBO, 0x17", Procedure8_4_1_2_2),
          TestProcedure("8.4.1.2.3", "Analog Output, SBO, to Uninstalled Point", Procedure8_4_1_2_3),
          TestProcedure("8.4.1.2.4", "Analog Output, SBO, Execute Issued After Time-out", Procedure8_4_1_2_4),
          TestProcedure(
            "8.4.1.2.5",
            "Analog Output, SBO, Execute Value does not match Select Value",
            Procedure8_4_1_2_5),
          TestProcedure("8.4.1.2.6", "Analog Output, SBO, Select 0x28, Execute 0x17", Procedure8_4_1_2_6),
          TestProcedure("8.4.1.2.7", "Analog Output, SBO, Configurable Device", Procedure8_4_1_2_7),
          TestProcedure("8.4.1.2.8", "Analog Output, SBO, Same Sequence Number Select Retries", Procedure8_4_1_2_8),
          TestProcedure(
            "8.4.1.2.9",
            "Analog Output, SBO, Incrementing Sequence Number Select Retries",
            Procedure8_4_1_2_9),
          TestProcedure("8.4.1.2.10", "Analog Output, SBO, Same Sequence Number Operate Retries", Procedure8_4_1_2_10),
          TestProcedure(
            "8.4.1.2.11",
            "Analog Output, SBO, Incrementing Sequence Number Operate Retries",
            Procedure8_4_1_2_11),
          TestProcedure("8.4.1.2.12", "Analog Output, SBO, Sequence number checking", Procedure8_4_1_2_12)
        ),
        section("8.4.2", "Analog Output Controls, Direct Operate")(
          TestProcedure("8.4.2.2.1", "Analog Output, Direct Operate", Procedure8_4_2_2_1),
          TestProcedure("8.4.2.2.2", "Analog Output, Direct Operate, to Uninstalled Point", Procedure8_4_2_2_2),
          TestProcedure("8.4.2.2.3", "Analog Output, Direct Operate, Configurable Device", Procedure8_4_2_2_3)
        ),
        section("8.4.3", "Analog Output Controls, Direct Operate No Acknowledgment")(
          TestProcedure("8.4.3.2.1", "Analog Output, Direct Operate, No Acknowledge", Procedure8_4_3_2_1),
          TestProcedure(
            "8.4.3.2.2",
            "Analog Output, Direct Operate, No Acknowledge, to an Uninstalled Point",
            Procedure8_4_3_2_2),
          TestProcedure(
            "8.4.3.2.3",
            "Analog Output, Direct Operate, No Acknowledge, Configurable Device",
            Procedure8_4_3_2_3)
        ),
        section("8.4.4", "Analog Output Controls, Multiple Objects")(
          TestProcedure("8.4.4.2", "Test Procedure", Procedure8_4_4_2)
        )
      ),
      section("8.5", "Class Data")(
        section("8.5.1", "Class 0")(
          TestProcedure("8.5.1.2", "Test Procedure", Procedure8_5_1_2)
        ),
        section("8.5.2", "Class 1")(
          TestProcedure("8.5.2.2.1", "Class 1 Data, 0x06", Procedure8_5_2_2_1),
          TestProcedure("8.5.2.2.2", "Class 1 Data, 0x07", Procedure8_5_2_2_2),
          TestProcedure("8.5.2.2.3", "Class 1 Data, 0x08", Procedure8_5_2_2_3),
          TestProcedure("8.5.2.2.4", "Class 1 Data Without Confirm", Procedure8_5_2_2_4)
        ),
        section("8.5.3", "Class 2")(
          TestProcedure("8.5.3.2.1", "Class 2 Data, 0x06", Procedure8_5_3_2_1),
          TestProcedure("8.5.3.2.2", "Class 2 Data, 0x07", Procedure8_5_3_2_2),
          TestProcedure("8.5.3.2.3", "Class 2 Data, 0x08", Procedure8_5_3_2_3),
          TestProcedure("8.5.3.2.4", "Class 2 Data Without Confirm", Procedure8_5_3_2_4)
        ),
        section("8.5.4", "Class 3")(
          TestProcedure("8.5.4.2.1", "Class 3 Data, 0x06", Procedure8_5_4_2_1),
          TestProcedure("8.5.4.2.2", "Class 3 Data, 0x07", Procedure8_5_4_2_2),
          TestProcedure("8.5.4.2.3", "Class 3 Data, 0x08", Procedure8_5_4_2_3),
          TestProcedure("8.5.4.2.4", "Class 3 Data Without Confirm", Procedure8_5_4_2_4)
        ),
        section("8.5.5", "Multiple Object Request")(
          TestProcedure("8.5.5.2.1", "Multiple Object Request, Class 1, 2, and 3", Procedure8_5_5_2_1),
          TestProcedure("8.5.5.2.2", "Multiple Object Request, Class 1, 2, 3, and 0", Procedure8_5_5_2_2)
        ),
        section("8.5.6", "Class Assignment Verification")(
          TestProcedure("8.5.6.2", "Test Procedure", Procedure8_5_6_2)
        )
      ),
      section("8.6", "Indications")(
        section("8.6.1", "Restart")(
          TestProcedure("8.6.1.2", "Test Procedure", Procedure8_6_1_2)
        ),
        section("8.6.2", "Bad Function")(
          TestProcedure("8.6.2.2", "Test Procedure", Procedure8_6_2_2)
        ),
        section("8.6.3", "Object Unknown")(
          TestProcedure("8.6.3.2", "Test Procedure", Procedure8_6_3_2)
        ),
        section("8.6.4", "Local")(
          TestProcedure(
            "8.6.4.2",
            "Test Procedure for Devices with Global Remote Supervisory Control",
            Procedure8_6_4_2),
          TestProcedure(
            "8.6.4.3",
            "Test Procedure for Devices with Individual Remote Supervisory Control",
            Procedure8_6_4_3)
        ),
        section("8.6.5", "Broadcast Address and All Stations Indication")(
          TestProcedure("8.6.5.2", "Test Procedure", Procedure8_6_5_2),
          TestProcedure("8.6.5.3", "Test Procedure", Procedure8_6_5_3),
          TestProcedure("8.6.5.4", "Test Procedure, Confirmed Response Options", Procedure8_6_5_4)
        ),
        section("8.6.6", "Buffer Overflow")(
          TestProcedure("8.6.6.2.1", "Buffer Overflow, Binary Input Change Event Buffers", Procedure8_6_6_2_1),
          TestProcedure("8.6.6.2.2", "Buffer Overflow, Analog Input Change Event Buffers", Procedure8_6_6_2_2),
          TestProcedure("8.6.6.2.3", "Buffer Overflow, Binary Counter Change Event Buffers", Procedure8_6_6_2_3),
          TestProcedure("8.6.6.2.4", "Double-bit Input Change Event Buffers", Procedure8_6_6_2_4)
        )
      ),
      section("8.7", "Time")(
        section("8.7.1", "Delay Measurement")(
          TestProcedure("8.7.1.2", "Test Procedure", Procedure8_7_1_2)
        ),
        section("8.7.2", "Synchronization")(
          TestProcedure("8.7.2.2", "Test Procedure", Procedure8_7_2_2)
        )
      ),
      section("8.8", "Cold Restart")(
        TestProcedure("8.8.2", "Test Procedure", Procedure8_8_2)
      ),
      section("8.9", "Application Layer Fragmentation")(
        section("8.9.1", "Use of FIR, FIN and SEQUENCE in Fragmentation")(
          TestProcedure("8.9.1.2", "Test Procedure", Procedure8_9_1_2)
        ),
        section("8.9.2", "Use of Confirmation in Fragmentation")(
          TestProcedure("8.9.2.2", "Test Procedure", Procedure8_9_2_2)
        )
      ),
      section("8.10", "Multi-Drop Support")(
        unimplemented("8.10.2", "Test Procedure")
      ),
      section("8.11", "Unsolicited Responses")(
        section("8.11.2", "Test Procedure")(
          TestProcedure("8.11.2.1", "Unsolicited Response Configuration/Startup", Procedure8_11_2_1),
          TestProcedure("8.11.2.2", "Unsolicited Response - Class 1 Data", Procedure8_11_2_2),
          TestProcedure("8.11.2.3", "Unsolicited Response - Class 2 Data", Procedure8_11_2_3),
          TestProcedure("8.11.2.4", "Unsolicited Response - Class 3 Data", Procedure8_11_2_4),
          TestProcedure("8.11.2.5", "Unsolicited and Polled Responses", Procedure8_11_2_5),
          TestProcedure("8.11.2.6", "Unsolicited Responses Off", Procedure8_11_2_6)
        )
      ),
      section("8.12", "Collision Avoidance")(
        unimplemented("8.12.2", "Test Procedure")
      ),
      section("8.13", "Binary Inputs")(
        TestProcedure("8.13.2.1", "Device does not have Binary Inputs", Procedure8_13_2_1),
        TestProcedure("8.13.2.2", "Device has Binary Inputs", Procedure8_13_2_2),
        TestProcedure("8.13.2.3", "Device supports Binary Inputs, Configurable Device", Procedure8_13_2_3)
      ),
      section("8.14", "Binary Input Change")(
        TestProcedure("8.14.2.1", "Device does not support Binary Input Changes", Procedure8_14_2_1),
        TestProcedure("8.14.2.2", "Binary Input Change, 0x06", Procedure8_14_2_2),
        TestProcedure("8.14.2.3", "Binary Input Change, 0x07", Procedure8_14_2_3),
        TestProcedure("8.14.2.4", "Binary Input Change, 0x08", Procedure8_14_2_4),
        TestProcedure("8.14.2.5", "Binary Input Change Without Confirm", Procedure8_14_2_5),
        TestProcedure("8.14.2.6", "Binary Input Change Without Time, 0x06", Procedure8_14_2_6),
        TestProcedure("8.14.2.7", "Binary Input Change Without Time, 0x07", Procedure8_14_2_7),
        TestProcedure("8.14.2.8", "Binary Input Change Without Time, 0x08", Procedure8_14_2_8),
        TestProcedure("8.14.2.9", "Binary Input Change With Time, 0x06", Procedure8_14_2_9),
        TestProcedure("8.14.2.10", "Binary Input Change With Time, 0x07", Procedure8_14_2_10),
        TestProcedure("8.14.2.11", "Binary Input Change With Time, 0x08", Procedure8_14_2_11),
        TestProcedure("8.14.2.12", "Binary Input Change With Relative Time, 0x06", Procedure8_14_2_12),
        TestProcedure("8.14.2.13", "Binary Input Change With Relative Time, 0x07", Procedure8_14_2_13),
        TestProcedure("8.14.2.14", "Binary Input Change With Relative Time, 0x08", Procedure8_14_2_14),
        TestProcedure("8.14.2.15", "Binary Input Change with Relative Time, Long Interval", Procedure8_14_2_15)
      ),
      section("8.15", "Common Time of Occurrence")(
        TestProcedure("8.15.2", "Test Procedure", Procedure8_15_2)
      ),
      section("8.16", "Binary Counters")(
        section("8.16.1", "Binary Counters, Running")(
          TestProcedure("8.16.1.2.1", "Device does not have Binary Counters", Procedure8_16_1_2_1),
          TestProcedure("8.16.1.2.2", "Device responds with Binary Counters", Procedure8_16_1_2_2),
          TestProcedure("8.16.1.2.3", "Device supports Binary Counters, Configurable Device", Procedure8_16_1_2_3)
        ),
        section("8.16.2", "Binary Counters, Frozen")(
          TestProcedure("8.16.2.2.1", "Device does not have Frozen Counters", Procedure8_16_2_2_1),
          TestProcedure("8.16.2.2.2", "Binary Counter, Freeze", Procedure8_16_2_2_2),
          TestProcedure("8.16.2.2.3", "Binary Counter, Freeze And Clear", Procedure8_16_2_2_3),
          TestProcedure("8.16.2.2.4", "Binary Counter, Freeze, No Acknowledge", Procedure8_16_2_2_4),
          TestProcedure("8.16.2.2.5", "Binary Counter, Freeze And Clear, No Acknowledge", Procedure8_16_2_2_5)
        )
      ),
      section("8.17", "Binary Counters, Event")(
        section("8.17.2", "Test Procedure")(
          TestProcedure("8.17.2.1", "Device does not support Binary Counter Events", Procedure8_17_2_1),
          TestProcedure("8.17.2.2", "Binary Counters, Event, 0x06", Procedure8_17_2_2),
          TestProcedure("8.17.2.3", "Binary Counters, Event, 0x07", Procedure8_17_2_3),
          TestProcedure("8.17.2.4", "Binary Counters, Event, 0x08", Procedure8_17_2_4),
          TestProcedure("8.17.2.5", "Binary Counters, Event Without Confirm", Procedure8_17_2_5)
        )
      ),
      section("8.18", "Analog Input")(
        section("8.18.2", "Test Procedure")(
          TestProcedure("8.18.2.1", "Device does not have Analog Inputs", Procedure8_18_2_1),
          TestProcedure("8.18.2.2", "Device has Analog Inputs", Procedure8_18_2_2)
        )
      ),
      section("8.19", "Analog Change Event")(
        section("8.19.2", "Test Procedure")(
          TestProcedure("8.19.2.1", "Analog Input Change", Procedure8_19_2_1),
          TestProcedure("8.19.2.2", "Analog Input Change, Without Confirm", Procedure8_19_2_2)
        )
      ),
      section("8.20", "Multiple Read Requests")(
        TestProcedure("8.20.2", "Test Procedure", Procedure8_20_2)
      ),
      section("8.21", "Double-bit Inputs")(
        section("8.21.2", "Test Procedure")(
          TestProcedure("8.21.2.1", "Device has Double-bit Inputs", Procedure8_21_2_1),
          TestProcedure("8.21.2.2", "Device supports Double-bit Inputs, Configurable Device", Procedure8_21_2_2)
        )
      ),
      section("8.22", "Double-bit Input Change")(
        section("8.22.2", "Test Procedure")(
          TestProcedure("8.22.2.1", "Device does not support Double-bit Input Changes", Procedure8_22_2_1),
          TestProcedure("8.22.2.2", "Double-bit Input Change, 0x06", Procedure8_22_2_2),
          TestProcedure("8.22.2.3", "Double-bit Input Change, 0x07", Procedure8_22_2_3),
          TestProcedure("8.22.2.4", "Double-bit Input Change, 0x08", Procedure8_22_2_4),
          TestProcedure("8.22.2.5", "Double-bit Input Change Without Confirm", Procedure8_22_2_5),
          TestProcedure("8.22.2.6", "Double-bit Input Change Without Time, 0x06", Procedure8_22_2_6),
          TestProcedure("8.22.2.7", "Double-bit Input Change Without Time, 0x07", Procedure8_22_2_7),
          TestProcedure("8.22.2.8", "Double-bit Input Change Without Time, 0x08", Procedure8_22_2_8),
          TestProcedure("8.22.2.9", "Double-bit Input Change With Time, 0x06", Procedure8_22_2_9),
          TestProcedure("8.22.2.10", "Double-bit Input Change With Time, 0x07", Procedure8_22_2_10),
          TestProcedure("8.22.2.11", "Double-bit Input Change With Time, 0x08", Procedure8_22_2_11),
          TestProcedure("8.22.2.12", "Double-bit Input Change With Relative Time, 0x06", Procedure8_22_2_12),
          TestProcedure("8.22.2.13", "Double-bit Input Change With Relative Time, 0x07", Procedure8_22_2_13),
          TestProcedure("8.22.2.14", "Double-bit Input Change With Relative Time, 0x08", Procedure8_22_2_14),
          TestProcedure("8.22.2.15", "Double-bit Input Change with Relative Time, Long Interval", Procedure8_22_2_15)
        )
      ),
      section("8.23", "Device Attributes")(
        section("8.23.2", "Test Procedure")(
          TestProcedure("8.23.2.1", "Variation 255 – list of attribute variations", Procedure8_23_2_1),
          TestProcedure("8.23.2.2", "Variation 254 – non-specific all attributes request", Procedure8_23_2_2),
          TestProcedure("8.23.2.3", "Variation nnn – specific attributes read request", Procedure8_23_2_3),
          TestProcedure("8.23.2.4", "Variation nnn – specific attributes write request", Procedure8_23_2_4)
        )
      )
    )
  )
}
