// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app._

/**
 * Perform a verification step on a received application layer message
 */
trait MessageValidation {

  def description: List[Comment]
  def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck]

  implicit def toResponseValidation: ResponseValidation = {
    val self = this
    new ResponseValidation {
      override def description: List[Comment] = self.description
      override def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
        responses.flatMap(self.validate(reporter, _))
      }
    }
  }

  // Validate only the first fragment, e.g. for an IIN bit the DUT reports in the response's first fragment
  def onFirstFragment: ResponseValidation = {
    val self = this
    new ResponseValidation {
      override def description: List[Comment] = self.description
      override def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] =
        responses.headOption.toList.flatMap(self.validate(reporter, _))
    }
  }

  // Validate once against all fragments, with their objects combined into one message. Use this for checks that
  // must see the whole response (e.g. "all points are reported") rather than each fragment on its own.
  def acrossFragments: ResponseValidation = {
    val self = this
    new ResponseValidation {
      override def description: List[Comment] = self.description
      override def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
        responses match {
          case Nil => Nil
          case first :: _ => self.validate(reporter, first.copy(headers = responses.flatMap(_.headers)))
        }
      }
    }
  }

  def &&(other: MessageValidation): MessageValidation = {
    val self = this
    new MessageValidation {
      override def description: List[Comment] = self.description ::: other.description
      override def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] = {
        self.validate(reporter, message) ::: other.validate(reporter, message)
      }
    }
  }

  def &&(other: ResponseValidation): ResponseValidation = this.toResponseValidation && other
}

object MessageValidation {

  // a validation that passes unless the check throws
  def check(desc: Comment*)(f: Message => Unit): MessageValidation = new MessageValidation {
    override def description: List[Comment] = desc.toList
    override def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] = {
      f(message)
      Nil
    }
  }

  val none: MessageValidation = check()(_ => ())
}

trait IINValidation extends MessageValidation {

  def validate(iin: IIN): List[DeferredCheck]

  override def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] = {
    message.iin match {
      case Some(iin) => validate(iin)
      case None => throw new Exception("No IIN present")
    }
  }
}

/**
 * Perform a verification step on a set of responses
 */
trait ResponseValidation {
  def description: List[Comment]
  def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck]

  def &&(other: ResponseValidation): ResponseValidation = {
    val self = this
    new ResponseValidation {
      override def description: List[Comment] = self.description ::: other.description
      override def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
        self.validate(reporter, responses) ::: other.validate(reporter, responses)
      }
    }
  }

  def &&(other: MessageValidation): ResponseValidation = this && other.toResponseValidation
}

object ResponseValidation {
  val none: ResponseValidation = new ResponseValidation {
    override def description: List[Comment] = Nil
    override def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = Nil
  }
}
