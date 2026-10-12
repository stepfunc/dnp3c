// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.dnp3.Comment

object FrameValidator {
  val none: FrameValidator = new FrameValidator {
    override def description: List[Comment] = Nil
    override def validate(frame: LinkFrame): Unit = ()
  }
}

// checks a link frame immediately, throwing if it's invalid
trait FrameValidator {
  def description: List[Comment]
  def validate(frame: LinkFrame): Unit
  def function: LinkFrame => Unit = (f: LinkFrame) => validate(f)

  def &&(other: FrameValidator): FrameValidator = {
    val self = this
    new FrameValidator {
      override def description: List[Comment] = self.description ::: other.description
      override def validate(frame: LinkFrame): Unit = {
        self.validate(frame)
        other.validate(frame)
      }
    }
  }
}
