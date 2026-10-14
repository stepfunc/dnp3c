// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api.options.AttributeOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ AttributeListEntry, AttributeValue, ReceivedAttribute }

// validations of device attribute (group 0) responses
object AttributeValidations {

  // 254 (all attributes) and 255 (list of variations) are only requested, and 0 is reserved
  private val notAttributes = Set(0, 254, 255)

  // IEEE 1815 Table 14-5 allows device attribute responses to use qualifier 0x00 or 0x17
  private val responseQualifiers = Set(Qualifier.ONE_BYTE_START_STOP, Qualifier.ONE_BYTE_COUNT_AND_INDEX)

  // the attributes in a response, which must use an allowed qualifier and not repeat a variation within a set
  private def received(message: Message): List[ReceivedAttribute] = {
    message.headers.find(h => !responseQualifiers.contains(h.qualifier)).foreach { h =>
      throw new Exception(s"Expected qualifier 0x00 or 0x17 for a device attribute: $h")
    }
    val attributes = ReceivedAttribute.from(message)
    attributes.diff(attributes.distinctBy(a => (a.set, a.variation))).headOption.foreach { a =>
      throw new Exception(s"g0v${a.variation} is reported more than once for set ${a.set}")
    }
    attributes
  }

  private def checkStandardSetOnly(attributes: List[ReceivedAttribute]): Unit =
    attributes.find(_.set != 0).foreach(a =>
      throw new Exception(s"Expected only the standard set, but received set ${a.set}"))

  private def checkSameVariations(name: String, actual: List[Int], expected: List[Int]): Unit = {
    val missing = expected.filterNot(actual.contains)
    val unexpected = actual.filterNot(expected.contains)
    if (missing.nonEmpty || unexpected.nonEmpty) {
      throw new Exception(
        s"$name differ from the Device Profile, missing: [${missing.mkString(", ")}], unexpected: [${unexpected.mkString(", ")}]")
    }
  }

  // a value that a device attribute may have, i.e. not a list of variations and not malformed
  private def checkValue(attribute: ReceivedAttribute): AttributeValue = attribute.value match {
    case v @ (_: AttributeValue.Invalid | _: AttributeValue.Unknown | _: AttributeValue.VariationList) =>
      throw new Exception(s"g0v${attribute.variation} in set ${attribute.set} has a bad value: ${v.description}")
    case v => v
  }

  private def sameValue(a: AttributeValue, b: AttributeValue): Boolean = (a, b) match {
    case (AttributeValue.FloatingPoint(x), AttributeValue.FloatingPoint(y)) => java.lang.Double.compare(x, y) == 0
    case _ => a == b
  }

  private def describe(variations: List[Int]): String = if (variations.isEmpty) "none" else variations.mkString(", ")

  def verifyAttributeList(config: AttributeOptions.Config, onlyStandardSet: Boolean): MessageValidation =
    MessageValidation.check((List[Comment](
      "Verify that the response lists attribute variations (g0v255), each with properties 0x00 (read only) or 0x01 (read/write)",
      "Verify that variations 254 and 255 are not listed",
      s"Verify that the standard set lists the variations in the Device Profile: ${describe(config.supported)}",
      s"Verify that the read/write variations are: ${describe(config.writable)}"
    ) ++ Option.when(onlyStandardSet)(Comment("Verify that only the standard set (index 0) is reported")))*) {
      message =>
        val attributes = received(message)
        attributes.find(_.variation != 255).foreach(a =>
          throw new Exception(s"Expected only g0v255, but received g0v${a.variation}"))
        if (onlyStandardSet) checkStandardSetOnly(attributes)

        val lists: Map[Int, List[AttributeListEntry]] = attributes.map { a =>
          a.value match {
            case AttributeValue.VariationList(entries) => (a.set, entries)
            case v => throw new Exception(
                s"Expected a list of attribute variations for set ${a.set}, but received ${v.description}")
          }
        }.toMap

        lists.foreach { case (set, entries) =>
          entries.find(e => e.properties != 0 && e.properties != 1).foreach { e =>
            throw new Exception(s"Set $set: variation ${e.description} has properties other than 0x00 or 0x01")
          }
          val variations = entries.map(e => 0xFF & e.variation)
          variations.find(notAttributes.contains).foreach(v => throw new Exception(s"Set $set lists variation $v"))
          if (variations.distinct != variations) throw new Exception(s"Set $set lists a variation more than once")
        }

        val standard = lists.getOrElse(0, throw new Exception("The response doesn't list the standard set (index 0)"))
        checkSameVariations("The standard set variations", standard.map(e => 0xFF & e.variation), config.supported)
        checkSameVariations(
          "The standard set read/write variations",
          standard.filter(_.properties == 1).map(e => 0xFF & e.variation),
          config.writable)
    }

  def verifyAllAttributes(config: AttributeOptions.Config, onlyStandardSet: Boolean): MessageValidation =
    MessageValidation.check((List[Comment](
      "Verify that the response contains only device attributes (group 0), each with a valid value",
      "Verify that variations 254 and 255 are not reported",
      s"Verify that the standard set reports the variations in the Device Profile: ${describe(config.supported)}"
    ) ++ Option.when(onlyStandardSet)(Comment("Verify that only the standard set (index 0) is reported")))*) {
      message =>
        val attributes = received(message)
        if (onlyStandardSet) checkStandardSetOnly(attributes)
        attributes.find(a => notAttributes.contains(a.variation)).foreach { a =>
          throw new Exception(s"Variation ${a.variation} is reported for set ${a.set}")
        }
        attributes.foreach(checkValue)
        checkSameVariations(
          "The standard set variations",
          attributes.filter(_.set == 0).map(_.variation),
          config.supported)
    }

  def verifySingleAttribute(variation: Int): MessageValidation = MessageValidation.check(
    s"Verify that the response contains only g0v$variation for the standard set (index 0), with a valid value") {
    message =>
      received(message) match {
        case List(a) if a.variation == variation && a.set == 0 => checkValue(a)
        case attributes => throw new Exception(
            s"Expected only g0v$variation for the standard set, but received ${attributes.size} attribute(s)")
      }
  }

  // compares a single attribute response with the same attribute in a recorded response
  def sameAsRecorded(recorder: MessageRecorder, variation: Int): MessageValidation =
    MessageValidation.check(s"Verify that g0v$variation has the same value as in the all attributes response") {
      message =>
        def find(m: Message): ReceivedAttribute =
          ReceivedAttribute.from(m).find(a => a.variation == variation && a.set == 0)
            .getOrElse(throw new Exception(s"No g0v$variation for the standard set"))
        val recorded =
          find(recorder.previousMessage.getOrElse(throw new Exception("No all attributes response was recorded")))
        val current = find(message)
        if (!sameValue(recorded.value, current.value)) {
          throw new Exception(
            s"g0v$variation was ${recorded.value.description}, but is now ${current.value.description}")
        }
    }

}
