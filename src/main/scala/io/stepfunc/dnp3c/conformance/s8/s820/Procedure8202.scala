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

object Procedure8202 extends TestProcedure {

  override def id: String = "8.20.2"

  override def description: String = "Test Procedure"

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    case class Item(supported: Boolean, variation: Variation)

    def getObjectHeader(item: Item): List[ObjectHeader] = {
      if (item.supported) List(AllObjectsHeader(item.variation)) else Nil
    }

    def static(points: BooleanOption, variation: Variation): Item = Item(options.get(points), variation)

    // event objects are only requested if the DUT has both the points and their change events
    def events(points: BooleanOption, events: BooleanOption, variation: Variation): Item =
      Item(options.get(points) && options.get(events), variation)

    def getStaticHeaders: List[ObjectHeader] = List(
      static(Inputs.binary.points, Group1Var0),
      static(Inputs.doubleBit.points, Group3Var0),
      static(Inputs.counter.points, Group20Var0),
      static(Inputs.frozenCounter.points, Group21Var0),
      static(Inputs.analog.points, Group30Var0),
      static(Outputs.binary.statusPoints, Group10Var0),
      static(Outputs.analog.statusPoints, Group40Var0)
    ).flatMap(getObjectHeader)

    def getEventHeaders: List[ObjectHeader] = List(
      events(Inputs.binary.points, Inputs.binary.events, Group2Var0),
      events(Inputs.doubleBit.points, Inputs.doubleBit.events, Group4Var0),
      events(Inputs.counter.points, Inputs.counter.events, Group22Var0),
      events(Inputs.frozenCounter.points, Inputs.frozenCounter.events, Group23Var0),
      events(Inputs.analog.points, Inputs.analog.events, Group32Var0),
      events(Outputs.binary.statusPoints, Outputs.binary.events, Group11Var0),
      events(Outputs.analog.statusPoints, Outputs.analog.events, Group42Var0)
    ).flatMap(getObjectHeader)

    def headers = getEventHeaders ::: getStaticHeaders

    List(
      1 --> send(Requests.readHeaders(Sequence.zero, headers)),
      (2 to 4) --> expectAtLeastOneResponse(
        Sequence.zero,
        without(IIN.iin2x) && verifyOnlyRequestedObjects(headers) &&
          PluginValidations.verifyReturnedEvents().acrossFragments &&
          PluginValidations.verifyAllStaticValues(isClass0 = false).acrossFragments &&
          verifyEventDataPrecedesStaticData && verifyStaticOrder(getStaticHeaders).acrossFragments
      )
    )
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
