// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ AttributeOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.v27.AttributeValidations._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.{ MessageRecorder, Requests }
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Message, MutableSequence }
import io.stepfunc.dnp3c.dnp3.app.objects.Group0

object Procedure8_23_2_2 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(SupportOptions.deviceAttributes)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val config = AttributeOptions.getConfig(options)
    val seq = MutableSequence.zero

    // steps first to first + 4: read all attributes, check them against the Device Profile and the device, then check
    // that reading each one on its own gives the same value
    def readAll(first: Int, request: Message, onlyStandardSet: Boolean): List[LabeledStep] = {
      val recorder = new MessageRecorder
      List(
        first --> send(request),
        (first + 1 to first + 3) --> expectAtLeastOneResponse(
          request.ctrl.seq,
          without(IIN.errors) &&
            (verifyAllAttributes(config, onlyStandardSet) && recorder.record &&
              PluginValidations.verifyDeviceAttributes()).acrossFragments
        )
      ) ++ config.supported.map { variation =>
        val single = seq.increment()
        Label(s"step: ${first + 4}, g0v$variation") --> send(Requests.readAttribute(single, variation)) +
          expectSingleResponse(
            single,
            without(IIN.errors) && verifySingleAttribute(variation) &&
              sameAsRecorded(recorder, variation))
      }
    }

    readAll(1, Requests.readAttribute(seq, 254), onlyStandardSet = true) ++
      readAll(6, Requests.readAllObjects(seq.increment(), Group0(254)), onlyStandardSet = false)
  }
}
