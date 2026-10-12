// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.protocol.parsing.{ Field, UInt16LE, UInt8 }

object Validations {

  def matches(expected: ObjectHeader): MessageValidation = MessageValidation.check(
    ("Verify the response contains the exact object header: " :: Comment.levelOne(expected.toString) ::
      expected.contents.map(Comment.levelTwo).toList)*) { message =>
    message.headers match {
      case actual :: Nil =>
        if (actual != expected) throw new Exception("Object header was not an exact match")
      case _ =>
        throw new Exception("Unexpected number of headers: " + message.headers.length)
    }
  }

  def matches(expected: List[ObjectHeader]): MessageValidation = MessageValidation.check(
    ("Verify the response contains the exact object headers: " ::
      expected.flatMap(x => List(Comment.levelOne(x.toString)) ::: x.contents.map(Comment.levelTwo).toList))*) {
    message =>
      if (message.headers != expected) throw new Exception("Object headers were not an exact match")
  }

  // A command echo: an exact match and no error IIN bits. The expected header may report a status the DUT should give,
  // such as TOO_MANY_OPS for the command past its maximum
  def echoes(expected: ObjectHeader): MessageValidation = matches(expected) && without(IIN.errors)

  def isEmpty: MessageValidation = MessageValidation.check("Verify that the response is empty") { message =>
    if (message.headers.nonEmpty) throw new Exception("Expected an empty response")
  }

  def isNotEmpty: MessageValidation = MessageValidation.check("Verify that the response contains objects") { message =>
    if (message.headers.isEmpty) throw new Exception("Expected objects in the response")
  }

  // Null Response: no objects and no error IIN bits (IIN2-0, IIN2-1, IIN2-2)
  def isNull: MessageValidation = isEmpty && without(IIN.errors)

  // Null Response, or an Error Response whose error bits are all in `allowed` (an Error Response may contain data)
  def isNullOrErrorResponse(allowed: IIN): MessageValidation = new MessageValidation {
    override def description: List[Comment] =
      List("Verify that the response is a Null Response, or an Error Response whose only error bits are " + allowed)

    override def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] = {
      val errors = message.iin.getOrElse(throw new Exception("No IIN present")) & IIN.errors
      if (errors.isEmpty) isNull.validate(reporter, message)
      else without(IIN.errors & ~allowed).validate(reporter, message)
    }
  }

  // the first fragment of a response with more to follow (FIR set, FIN clear)
  def isFirstOfSeveralFragments: MessageValidation = MessageValidation.check(
    "Verify that this is the first of several fragments (FIR set, FIN clear)") { message =>
    if (!message.ctrl.fir.value) throw new Exception("Expected the first fragment of the response, but FIR is clear")
    if (message.ctrl.fin.value)
      throw new Exception(
        "Expected more fragments to follow, but FIN is set: the response fits in one fragment. If the data for a " +
          "response in several fragments may not have been set up, rerun the procedure with more data.")
  }

  // a response in more than one fragment
  def inSeveralFragments: ResponseValidation = new ResponseValidation {
    override def description: List[Comment] = List("Verify that the response is in several fragments")
    override def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
      if (responses.size < 2)
        throw new Exception(
          "The response is in one fragment. If the data for a response in several fragments may not have been set " +
            "up, rerun the procedure with more data.")
      Nil
    }
  }

  def isFirstFragment: MessageValidation =
    MessageValidation.check("Verify that this is the first fragment of the response (FIR set)") { message =>
      if (!message.ctrl.fir.value) throw new Exception("Expected the first fragment, but FIR is not set")
    }

  def verifyConfirmRequested: MessageValidation =
    MessageValidation.check("Verify that an application layer confirmation is requested.") { message =>
      if (!message.ctrl.con.value) {
        throw new Exception("Expected application layer confirmation request in response.")
      }
    }

  def verifyConfirmNotRequested: MessageValidation =
    MessageValidation.check("Verify that the response doesn't ask for an application layer confirmation.") { message =>
      if (message.ctrl.con.value) {
        throw new Exception("Expected application layer confirmation NOT to be requested in response.")
      }
    }

  // IEEE 1815 has a fragment ask for confirmation if another follows it or if it carries events: the last fragment of a
  // response that ends with static data needn't
  def verifyConfirmRequestedIfRequired: MessageValidation = MessageValidation.check(
    "Verify that each fragment followed by another, or carrying events, asks for an application layer confirmation.") {
    message =>
      if (!message.ctrl.con.value && (!message.ctrl.fin.value || message.measurements(PointType.Event).nonEmpty)) {
        throw new Exception("Expected application layer confirmation request in a fragment that requires one.")
      }
  }

  // a response carrying events: the events, as the plugin checks them; the objects they're reported in; and, as for
  // every event response, a request for confirmation and flags as Note 5 requires
  def eventResponse(events: MessageValidation, objects: MessageValidation): MessageValidation =
    events && verifyConfirmRequested && objects && verifyFlagsComplyWithNote5

  def verifyConfirmRequestedIfNotNull: MessageValidation = MessageValidation.check(
    "If the response carries data, verify that it asks for an application layer confirmation.") { message =>
    if (message.headers.nonEmpty && !message.ctrl.con.value) {
      throw new Exception("Expected confirmation on non-null response.")
    }
  }

  def verifyFlagsComplyWithNote5: MessageValidation =
    MessageValidation.check("Verify that the flags behave as Note 5 of Section 2 requires.") { message =>
      val allowedBinaryFlags = Set(0x01.toByte, 0x81.toByte)
      val allowedDoubleBitFlags = Set(0x01.toByte, 0x41.toByte, 0x81.toByte, 0xC1.toByte)
      val allowedOtherFlags = Set(0x01.toByte)

      def getAllowedFlagsForGroup(group: Group): Set[Byte] = group match {
        case Group1 | Group2 | Group10 | Group11 => allowedBinaryFlags // bit 7 is the state
        case Group3 => allowedDoubleBitFlags
        case Group4 => allowedDoubleBitFlags
        case _ => allowedOtherFlags
      }

      forEachField(message) {
        case (group, flags: Field.Types.Flags) =>
          val allowed = getAllowedFlagsForGroup(group)
          if (!allowed.contains(flags.value)) {
            throw new Exception("unexpected flags: " + flags.toString + " Allowed values for this group are: " +
              allowed.map(Field.Types.Flags.apply))
          }
        case _ =>
      }
    }

  // every field of every object in the message, with the object's group (bit-packed objects have no fields)
  private[helpers] def forEachField(message: Message)(f: (Group, Field) => Unit): Unit =
    message.headers.foreach {
      case header: ObjectHeaderWithPayload =>
        header.payload match {
          case objects: HasFieldCollection =>
            objects.getFieldCollection.foreach(_.fields.foreach(field => f(header.variation.group, field)))
          case _ =>
        }
      case _ =>
    }

  def verifyEventDataPrecedesStaticData: ResponseValidation = new ResponseValidation {

    override def description: List[Comment] = List(
      "Verify that, across every fragment, all event objects come before any static object"
    )

    override def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {

      def validateHeader(eventsComplete: Boolean, header: ObjectHeader): Boolean = header.variation.objectType match {
        case CommonTimeOfOccurrence =>
          if (eventsComplete) {
            throw new Exception(s"CTO variation follows static data: ${header.variation.key}")
          }
          eventsComplete
        case Event =>
          if (eventsComplete) {
            throw new Exception(s"Event variation follows static data: ${header.variation.key}")
          }
          eventsComplete
        case Static =>
          true
        case _ =>
          eventsComplete
      }

      responses.flatMap(r => r.headers).foldLeft(false)(validateHeader)
      Nil
    }
  }

  // generated events: at least one, all of the allowed variations
  def verifyEvents(allowed: Set[Variation]): MessageValidation = MessageValidation.check(
    s"Verify that the device responds with events, of only $allowed",
    "Verify that the objects are prefixed with 1-octet (qualifier 0x17) or 2-octet (qualifier 0x28) indexes"
  ) { message =>
    if (message.measurements(PointType.Event).isEmpty) throw new Exception("Expected events in the response")

    def validateVariation(v: Variation): Unit = {
      if (!allowed.contains(v)) {
        throw new Exception(s"Unexpected variation: $v")
      }
    }

    def validate(header: ObjectHeader): Unit = header match {
      case x: OneByteCountAndIndexHeader => validateVariation(x.variation)
      case x: TwoByteCountAndIndexHeader => validateVariation(x.variation)
      case _ => throw new Exception(s"Unexpected header: ${header.qualifier}")
    }

    message.headers.foreach(validate)
  }

  def verifyStaticResponse(allowed: Set[Variation]): MessageValidation = MessageValidation.check(
    s"Verify that the device responds with only variations: $allowed",
    "Verify that the objects are in start/stop ranges with 1-octet (qualifier 0x00) or 2-octet (qualifier 0x01) indexes"
  ) { message =>
    def validateVariation(v: Variation): Unit = {
      if (!allowed.contains(v)) {
        throw new Exception(s"Unexpected variation: $v")
      }
    }

    def validate(header: ObjectHeader): Unit = header match {
      case x: OneByteStartStopHeader => validateVariation(x.variation)
      case x: TwoByteStartStopHeader => validateVariation(x.variation)
      case _ => throw new Exception(s"Unexpected header: ${header.qualifier}")
    }

    message.headers.foreach(validate)
  }

  def verifyAnalogEvents: MessageValidation = verifyEvents(Set(Group32Var1, Group32Var2))

  def verifyOnlyTable86Contents: MessageValidation = verifyOnlyFrozenOrRunningCounters("table 8-6", Group21)

  def verifyOnlyTable85Contents: MessageValidation = verifyOnlyFrozenOrRunningCounters("table 8-5", Group20)

  // tables 8-5 and 8-6 list the counters (g20) and frozen counters (g21), in start/stop ranges
  private def verifyOnlyFrozenOrRunningCounters(table: String, group: Group): MessageValidation = {
    MessageValidation.check(
      s"Verify that the response only contains objects, variations, and qualifiers specified in $table") { message =>
      def validateHeader(header: ObjectHeader): Unit = {

        if (!SubsetLevel.isLevelOneOrTwo(header.variation.minSubsetLevel)) {
          throw new Exception(
            s"Unexpected group/variation ${header.variation.key} with minimum subset level: ${header.variation.minSubsetLevel}")
        }

        if (header.variation.group != group) {
          throw new Exception(s"Unexpected group/variation: ${header.variation.key}")
        }
        if (!staticQualifiers.contains(header.qualifier)) {
          throw new Exception("Unexpected qualifier: " + header.qualifier)
        }
        if (header.variation.id == 0) {
          throw new Exception("Unexpected variation 0")
        }
      }

      message.headers.foreach(validateHeader)
    }
  }

  // a delay measurement response: one g52v2 object (qualifier 0x07, count 1)
  def isDelayMeasurement: MessageValidation = MessageValidation.check(
    "Verify that the response is one g52v2 object (qualifier 0x07, count 1) carrying the measured delay in milliseconds") {
    message =>
      message.headers match {
        case OneByteCountHeader(Group52Var2, UInt8(1), _) :: Nil =>
        case headers => throw new Exception(s"Expected one g52v2 with count 1, but received: ${headers.mkString(", ")}")
      }
  }

  // a response holding a single event: one of `variations`, or a CTO (count 1) followed by one of `afterCto`
  def singleEvent(what: String, variations: Set[Variation], afterCto: Set[Variation] = Set.empty): MessageValidation =
    MessageValidation.check(s"Verify that the response contains a single $what") { message =>
      def oneOf(allowed: Set[Variation])(header: ObjectHeader): Boolean = header match {
        case OneByteCountAndIndexHeader(v, UInt8(1), _) => allowed.contains(v)
        case TwoByteCountAndIndexHeader(v, UInt16LE(1), _) => allowed.contains(v)
        case _ => false
      }
      // a CTO with qualifier 0x07, as Table 8-4 lists
      def oneCto(header: ObjectHeader): Boolean = header match {
        case OneByteCountHeader(_: TypeTraits.CommonTimeOfOccurrence, UInt8(1), _) => true
        case _ => false
      }
      val single = message.headers match {
        case event :: Nil => oneOf(variations)(event)
        case cto :: event :: Nil => oneCto(cto) && oneOf(afterCto)(event)
        case _ => false
      }
      if (!single) throw new Exception(s"Expected a single $what, but received: ${message.headers.mkString(", ")}")
    }

  // the qualifiers tables 8-3 to 8-6 give static objects (1- or 2-octet start/stop), events (1- or 2-octet count and
  // index) and CTOs (1-octet count)
  private val staticQualifiers = Set(Qualifier.ONE_BYTE_START_STOP, Qualifier.TWO_BYTE_START_STOP)
  private val eventQualifiers = Set(Qualifier.ONE_BYTE_COUNT_AND_INDEX, Qualifier.TWO_BYTE_COUNT_AND_INDEX)
  private val ctoQualifier = Qualifier.ONE_BYTE_COUNT

  // Tables 8-3 and 8-4 also list the double-bit objects (g3, g4). These are a Subset Level 4 feature that a
  // Level 2 device may optionally support (8.21, 8.22), so only accept them if the DUT is configured as having them.
  private def isTable83Or84Variation(variation: Variation, allowDoubleBit: Boolean): Boolean =
    SubsetLevel.isLevelOneOrTwo(variation.minSubsetLevel) ||
      (allowDoubleBit && (variation.group == Group3 || variation.group == Group4))

  // a response of events of the variations, with 1- or 2-octet indexes (qualifier 0x17 or 0x28), and CTOs of the ctos
  // variations (qualifier 0x07), a relative-time event preceded by a CTO
  def eventsWithCtos(
      variations: List[Variation],
      ctos: Set[Variation] = Set(Group51Var1, Group51Var2)): MessageValidation =
    MessageValidation.check(
      s"Verify that the response contains events of the variations $variations, and besides them only CTOs (${ctos
          .mkString(" or ")}, qualifier 0x07)",
      "Verify that the event objects are prefixed with 1-octet (qualifier 0x17) or 2-octet (qualifier 0x28) indexes",
      "Verify that each relative-time event is preceded by a CTO object (Table 8-4)"
    ) { message =>
      if (message.headers.forall(_.variation.objectType == CommonTimeOfOccurrence))
        throw new Exception("Expected events in the response")

      message.headers.foreach { header =>
        val valid =
          if (header.variation.objectType == CommonTimeOfOccurrence)
            ctos.contains(header.variation) && header.qualifier == ctoQualifier
          else variations.contains(header.variation) && eventQualifiers.contains(header.qualifier)
        if (!valid) throw new Exception(s"Unexpected ${header.variation} with qualifier ${header.qualifier}")
      }
      checkCtoPrecedesRelativeTimeEvents(message.headers)
    }

  // Table 8-4: a relative time event (g2v3, g4v3) must be preceded by a CTO object in the same fragment
  private[helpers] def checkCtoPrecedesRelativeTimeEvents(headers: List[ObjectHeader]): Unit = {
    val firstCto = headers.indexWhere(_.variation.objectType == CommonTimeOfOccurrence)
    val firstRelativeTime = headers.indexWhere(h => h.variation == Group2Var3 || h.variation == Group4Var3)
    if (firstRelativeTime >= 0 && (firstCto < 0 || firstCto > firstRelativeTime)) {
      throw new Exception(s"${headers(firstRelativeTime).variation} is not preceded by a CTO object")
    }
  }

  def verifyOnlyTable84Contents(allowDoubleBit: Boolean): MessageValidation = MessageValidation.check(
    "Verify that the response only contains objects, variations, and qualifiers specified in table 8-4") { message =>
    checkTableObjects(message, Set(Event, CommonTimeOfOccurrence), allowDoubleBit)
  }

  def verifyOnlyTable83Contents(allowDoubleBit: Boolean): MessageValidation = MessageValidation.check(
    "Verify the response only contains objects, variations and qualifiers specified in table 8-3") { message =>
    checkTableObjects(message, Set(Static), allowDoubleBit)
  }

  def verifyOnlyTable83OrTable84Contents(allowDoubleBit: Boolean): MessageValidation = MessageValidation.check(
    "Verify that the response holds only objects listed in tables 8-3 and 8-4",
    "Verify that event objects are prefixed with 1-octet (qualifier 0x17) or 2-octet (qualifier 0x28) indexes",
    "Verify that static objects are in start/stop ranges with 1-octet (qualifier 0x00) or 2-octet (qualifier 0x01) indexes"
  ) { message =>
    checkTableObjects(message, Set(Static, Event, CommonTimeOfOccurrence), allowDoubleBit)
  }

  // the objects tables 8-3 (static) and 8-4 (events, and the CTOs relative-time events follow) list: of the allowed
  // types, each with the qualifiers the tables give it
  private def checkTableObjects(message: Message, types: Set[ObjectType], allowDoubleBit: Boolean): Unit = {
    message.headers.foreach { header =>
      val variation = header.variation
      if (!isTable83Or84Variation(variation, allowDoubleBit)) {
        throw new Exception(
          s"Unexpected group/variation ${variation.key} with minimum subset level: ${variation.minSubsetLevel}")
      }
      if (!types.contains(variation.objectType)) {
        throw new Exception(s"Unexpected group/variation: ${variation.key} with type ${variation.objectType}")
      }
      checkQualifier(header)
    }
    checkCtoPrecedesRelativeTimeEvents(message.headers)
  }

  // a response to a read of several groups: only static and event objects of the groups requested, and, if events were
  // requested, the CTOs relative-time events follow; each with the qualifiers tables 8-3 and 8-4 give its type
  def verifyOnlyRequestedObjects(requested: List[ObjectHeader]): MessageValidation = {
    val groups = requested.map(_.variation.group).toSet
    val eventsRequested = requested.exists(_.variation.objectType == EventVariationZero)
    val ctos = if (eventsRequested) " and the CTOs relative-time events follow" else ""
    MessageValidation.check(
      s"Verify that the response holds only objects of the groups requested (${groups.map(g => s"g${g.id}").mkString(", ")})$ctos",
      "Verify that event objects are prefixed with 1-octet (qualifier 0x17) or 2-octet (qualifier 0x28) indexes",
      "Verify that static objects are in start/stop ranges with 1-octet (qualifier 0x00) or 2-octet (qualifier 0x01) indexes"
    ) { message =>
      message.headers.foreach { header =>
        val variation = header.variation
        val valid = variation.objectType match {
          case Static | Event => groups.contains(variation.group)
          case CommonTimeOfOccurrence => eventsRequested
          case _ => false
        }
        if (!valid)
          throw new Exception(s"Unexpected group/variation: ${variation.key} with type ${variation.objectType}")
        checkQualifier(header)
      }
      checkCtoPrecedesRelativeTimeEvents(message.headers)
    }
  }

  // the qualifiers tables 8-3 and 8-4 give the object's type
  private def checkQualifier(header: ObjectHeader): Unit = {
    val qualifiers = header.variation.objectType match {
      case Static => staticQualifiers
      case Event => eventQualifiers
      case _ => Set(ctoQualifier)
    }
    if (!qualifiers.contains(header.qualifier)) throw new Exception("Unexpected qualifier: " + header.qualifier)
  }

  def containing(expected: IIN): MessageValidation = new IINValidation {
    override def description: List[Comment] = List("Verify that the IIN field contains " + expected)
    override def validate(iin: IIN): List[DeferredCheck] = {
      if ((iin & expected) != expected) throw new Exception("Expected all of: " + expected + ", but received " + iin)
      Nil
    }
  }

  def containingAnyOf(expected: IIN): MessageValidation = new IINValidation {
    override def description: List[Comment] = List("Verify that the IIN field contains at least one of " + expected)
    override def validate(iin: IIN): List[DeferredCheck] = {
      if ((iin & expected).isEmpty)
        throw new Exception("Expected at least one of: " + expected + " but received " + iin)
      Nil
    }
  }

  def without(notAllowed: IIN): MessageValidation = new IINValidation {
    override def description: List[Comment] = List("Verify that the IIN field does not contain bits " + notAllowed)
    override def validate(iin: IIN): List[DeferredCheck] = {
      val bits = iin & notAllowed
      if (!bits.isEmpty) throw new Exception("Not allowed to receive bits: " + bits)
      Nil
    }
  }

}
