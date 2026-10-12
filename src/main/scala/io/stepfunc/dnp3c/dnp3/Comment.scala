// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

case class Comment(value: String, indentation: Int) {

  override def toString: String = {
    val prefix: String = "   " * (indentation + 1)
    s"$prefix- $value"
  }
}

object Comment {

  implicit def apply(value: String): Comment = Comment(value, 0)
  def levelOne(value: String): Comment = Comment(value, 1)
  def levelTwo(value: String): Comment = Comment(value, 2)

}
