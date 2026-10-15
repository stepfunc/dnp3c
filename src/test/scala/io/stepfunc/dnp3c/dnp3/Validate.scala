// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.protocol.Hex

import scala.util.Try

// whether a validation accepts a response; the response is built before the validation runs, so a fixture that doesn't
// parse fails the test rather than counting as a rejection
object Validate {

  def passes(validation: MessageValidation, message: Message): Boolean =
    Try(validation.validate(StubDriver.reporter(), message)).isSuccess

  // a response of these objects in hex, after "C0 81 00 00"
  def passes(validation: MessageValidation, objects: String): Boolean =
    passes(validation, Message.parse(Hex.from("C0 81 00 00 " + objects)))

  def passes(validation: ResponseValidation, fragments: List[Message]): Boolean =
    Try(validation.validate(StubDriver.reporter(), fragments)).isSuccess
}
