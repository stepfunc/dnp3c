// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s7

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.Comment
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link.{ FrameValidator, LinkFrame }
import io.stepfunc.dnp3c.dnp3.transport.Transport

object Procedure72 extends ProcedureDefinition {

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val details: List[Comment] = List(
      "Verify that a reply longer than 249 octets starts with a transport segment that has FIR=1 and FIN=0",
      "Verify that the transport segments between the first and the last have FIR=0 and FIN=0",
      "Verify that a reply spanning multiple transport segments ends with FIR=0 and FIN=1",
      "Verify that each subsequent transport segment advances SEQ by one, modulo 64"
    )

    List(
      1 --> PluginSteps.cyclePower,
      2 --> readClass0(Sequence.zero),
      (3 to 7) --> expectAtLeastOneResponse(Sequence.zero, oneSegmentIfItFits) + describe(details)
    )
  }

  // step 7: a reply that fits one link frame (249 octets of application data) is one segment, with FIR and FIN set
  private def oneSegmentIfItFits: FrameValidator = new FrameValidator {
    private var segments = 0
    private var octets = 0

    override def description: List[Comment] =
      List("Verify that a reply of 249 octets or less is a single transport segment, with FIR=1 and FIN=1")

    override def validate(frame: LinkFrame): Unit = frame.data match {
      case header :: payload =>
        val transport = Transport.function(header)
        if (transport.fir) {
          segments = 0
          octets = 0
        }
        segments += 1
        octets += payload.size
        if (transport.fin && segments > 1 && octets <= Transport.maxPayload)
          throw new Exception(s"A reply of $octets octets, which fits one link frame, was sent in $segments segments")
      case Nil =>
    }
  }
}
