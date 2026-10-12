// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app.Message

class MessageRecorder {
  private var previous: Option[Message] = None

  def previousMessage: Option[Message] = previous

  def record: MessageValidation = new MessageValidation {
    override def description: List[Comment] = Nil

    override def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] = {
      previous = Some(message)
      Nil
    }
  }
}
