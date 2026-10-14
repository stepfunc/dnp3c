// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s820

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ MessageValidation, Requests }

/**
 * 8.20.2's Multiple Object Types
 *
 * @param staticInRequestOrder whether the static objects must come back in the order requested, as 2.7 requires;
 *   3.1r1 requires only that events come before them
 * @param onlyReadable whether to request only the inputs the DUT can read, which a subset level 1 device needn't all
 *   be able to, as 3.1r1 allows
 */
class MultipleObjectTypes(staticInRequestOrder: Boolean, onlyReadable: Boolean) extends ProcedureDefinition {

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val (getEventHeaders, getStaticHeaders) = MultipleObjectTypes.requested(options, onlyReadable)
    def headers = getEventHeaders ::: getStaticHeaders
    // the plugin checks that every static point is reported, so only a request for every type the DUT has can be
    // checked against it
    val (allEvents, allStatic) = MultipleObjectTypes.requested(options, onlyReadable = false)
    val staticValues =
      if (allStatic == getStaticHeaders) PluginValidations.verifyAllStaticValues(isClass0 = false).acrossFragments
      else
        MessageValidation.check(
          "The DUT can't read some of its inputs on their own, so the static values aren't checked against the " +
            "plugin, which expects every point")(_ => ()).acrossFragments

    val responses = without(IIN.iin2x) && verifyOnlyRequestedObjects(headers) &&
      PluginValidations.verifyReturnedEvents().acrossFragments &&
      staticValues &&
      verifyEventDataPrecedesStaticData

    List(
      1 --> send(Requests.readHeaders(Sequence.zero, headers)),
      (2 to 4) --> expectAtLeastOneResponse(
        Sequence.zero,
        if (staticInRequestOrder)
          responses && MultipleObjectTypes.verifyStaticOrder(getStaticHeaders).acrossFragments
        else responses
      )
    )
  }
}

object MultipleObjectTypes {

  /**
   * The event and the static objects to request: one header of each type the DUT has, and of the inputs, if onlyReadable,
   * only those it can read
   */
  def requested(options: OptionSource, onlyReadable: Boolean): (List[ObjectHeader], List[ObjectHeader]) = {

    case class Item(supported: Boolean, variation: Variation)

    def getObjectHeader(item: Item): List[ObjectHeader] = {
      if (item.supported) List(AllObjectsHeader(item.variation)) else Nil
    }

    def static(points: BooleanOption, variation: Variation): Item = Item(options.get(points), variation)
    def input(input: InputOptions, variation: Variation): Item =
      Item(options.get(input.points) && (!onlyReadable || options.get(input.reads)), variation)

    // event objects are only requested if the DUT has both the points and their change events
    def events(points: BooleanOption, events: BooleanOption, variation: Variation): Item =
      Item(options.get(points) && options.get(events), variation)
    def inputEvents(input: InputOptions, variation: Variation): Item = Item(
      options.get(input.points) && options.get(input.events) && (!onlyReadable || options.get(input.eventReads)),
      variation)

    val staticHeaders = List(
      input(Inputs.binary, Group1Var0),
      input(Inputs.doubleBit, Group3Var0),
      input(Inputs.counter, Group20Var0),
      input(Inputs.frozenCounter, Group21Var0),
      input(Inputs.analog, Group30Var0),
      static(Outputs.binary.statusPoints, Group10Var0),
      static(Outputs.analog.statusPoints, Group40Var0)
    ).flatMap(getObjectHeader)

    val eventHeaders = List(
      inputEvents(Inputs.binary, Group2Var0),
      inputEvents(Inputs.doubleBit, Group4Var0),
      inputEvents(Inputs.counter, Group22Var0),
      inputEvents(Inputs.frozenCounter, Group23Var0),
      inputEvents(Inputs.analog, Group32Var0),
      events(Outputs.binary.statusPoints, Outputs.binary.events, Group11Var0),
      events(Outputs.analog.statusPoints, Outputs.analog.events, Group42Var0)
    ).flatMap(getObjectHeader)

    (eventHeaders, staticHeaders)
  }

  // Step 4: the objects come back in the order of the request. Only static objects are checked: events of different types may
  // be interleaved (e.g. in time order), so for them only "events before static" is checked.
  private[s820] def verifyStaticOrder(requested: List[ObjectHeader]): MessageValidation =
    MessageValidation.check("Verify that the static objects follow the order of the request") { message =>
      var groups = requested.map(_.variation.group)

      message.headers.filter(_.variation.objectType == Static).foreach(header => {
        val group = header.variation.group
        while (!groups.headOption.contains(group)) {
          groups = groups match {
            case _ :: rest => rest
            case _ => throw new Exception(s"Static object ${header.variation} not in the order requested")
          }
        }
      })
    }

}

object Procedure8202 extends MultipleObjectTypes(staticInRequestOrder = true, onlyReadable = false)
