// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s89

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ MessageValidation, Requests }

object Procedure8912 extends ProcedureDefinition {

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val configurableFragmentSize = options.get(SupportOptions.configurableFragmentSize)
    val configuredSize = options.get(OutstationOptions.configuredFragmentSize)
    // step 7: a device that cannot produce multiple fragments is only checked for a valid single fragment
    val multiFragment = options.get(SupportOptions.multiFragmentResponses)

    val firstStep: Iterable[LabeledStep] = if (configurableFragmentSize) {
      List(
        1 --> PluginSteps.setMaxFragmentSize(2048)
      )
    } else {
      List(1 --> describe("DUT does not support configurable fragment size"))
    }

    def steps2To11(maxSize: Int): Iterable[LabeledStep] = {
      List(
        2 --> PluginSteps.cyclePower,
        3 --> {
          if (multiFragment) PluginSteps.generateMultiFragmentResponse() + send(Requests.readClass1230(Sequence.zero))
          else send(Requests.readClass1230(Sequence.zero))
        },
        // with multi-fragment data set up, the response must use it; otherwise one fragment is valid (step 7)
        (4 to 11) --> expectAtLeastOneResponse(
          Sequence.zero,
          if (multiFragment) multiFragmentValidations(maxSize).toResponseValidation && Validations.inSeveralFragments
          else multiFragmentValidations(maxSize).toResponseValidation
        )
      )
    }

    val steps12: Iterable[LabeledStep] = if (configurableFragmentSize && multiFragment) {
      List(12 --> PluginSteps.setMaxFragmentSize(configuredSize)) ++
        steps2To11(configuredSize)
    } else {
      List(12 --> describe("DUT does not support configurable fragment size or multi-fragment responses"))
    }

    firstStep ++ steps2To11(2048) ++ steps12
  }

  private def multiFragmentValidations(maxSize: Int) = MessageValidation.check(
    "Verify that the response holds valid data",
    "Verify that the first fragment carries the request's sequence number",
    "Verify that a response in one fragment has FIR=1 and FIN=1",
    "Verify that the first fragment of a response in several fragments has FIR=1 and FIN=0",
    "Verify that the fragments between the first and the last have FIR=0 and FIN=0",
    "Verify that a response spanning multiple application fragments ends with FIR=0 and FIN=1",
    "Verify that each subsequent application fragment advances SEQ by one, modulo 16",
    "Verify that the transport layer splits each fragment correctly",
    s"Verify that no fragment is larger than ${maxSize} bytes"
  ) { message =>
    val fragmentSize = message.toBytes.size
    if (fragmentSize > maxSize) {
      throw new Exception(s"Received fragment too big: ${fragmentSize} (max ${maxSize})")
    }
  }
}
