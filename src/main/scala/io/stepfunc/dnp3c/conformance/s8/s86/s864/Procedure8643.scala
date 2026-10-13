// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s864

import io.stepfunc.dnp3c.conformance.api.options.{ FlagTestOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group10Var0, Group10Var2 }
import io.stepfunc.dnp3c.dnp3.{ MessageValidation, Requests }
import io.stepfunc.dnp3c.protocol.Hex

object Procedure8643 extends ProcedureDefinition {

  override protected def requiredOptions = List(SupportOptions.individualRemoteSupervisoryControl)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val index = options.get(FlagTestOptions.individualRemoteSupervisoryOutputIndex)

    val onlineFlags: Set[Byte] = Set(0x01.toByte, 0x81.toByte)
    val onlineWithLocalForced: Set[Byte] = onlineFlags.map(b => (b | 0x10.toByte).toByte)

    List(
      1 --> describe(
        "This section needs a DUT whose binary outputs can be switched to local control."
      ),
      2 --> PluginSteps.enableRemoteSupervisoryControl(),
      3 --> send(Requests.readAllObjects(Sequence.zero, Group10Var0)),
      (4 to 5) --> expectAtLeastOneResponse(
        Sequence.zero,
        (statusObjects && without(IIN.LOCAL_CONTROL)).toResponseValidation &&
          outputFlags(onlineFlags, None).acrossFragments),
      6 --> PluginSteps.setIndividualRemoteSupervisoryControl(index.value),
      7 --> send(Requests.readAllObjects(Sequence.one, Group10Var0)),
      (8 to 9) --> expectAtLeastOneResponse(
        Sequence.one,
        (statusObjects && containing(IIN.LOCAL_CONTROL)).toResponseValidation &&
          outputFlags(onlineWithLocalForced, Some(index)).acrossFragments)
    )
  }

  // in each fragment, binary output status (g10v2) with 1- or 2-octet start/stop indexes
  private val statusObjects: MessageValidation =
    isNotEmpty && verifyStaticResponse(Set(Group10Var2)) && verifyConfirmRequestedIfRequired

  // across the response, the flags of each output, or of the one at index, which must be there
  private[s864] def outputFlags(allowsFlags: Set[Byte], index: Option[Index]): MessageValidation = {
    val objects = index match {
      case Some(x) => s"the object at index ${x.value}"
      case None => "each object"
    }

    MessageValidation.check(s"Verify that $objects has 'flags' in the ${allowsFlags.map(Hex.prefixed)}") { message =>
      val flags = message.measurements(PointType.Static).map(p => p.idx -> p.point.flags)
      def check(flag: Option[Byte]): Unit = flag match {
        case Some(value) if allowsFlags.contains(value) =>
        case _ => throw new Exception(s"unexpected flag value: ${flag.map(Hex.prefixed).getOrElse("none")}")
      }
      index match {
        case Some(x) =>
          val atIndex = flags.collect { case (idx, flag) if idx == x.value => flag }
          if (atIndex.isEmpty) throw new Exception(s"No object found at expected index: ${x.value}")
          atIndex.foreach(check)
        case None => flags.foreach { case (_, flag) => check(flag) }
      }
    }
  }

}
