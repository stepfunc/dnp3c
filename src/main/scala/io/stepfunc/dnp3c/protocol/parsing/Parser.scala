// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

/**
 * represents a parser for a field
 */
trait Parser[A] {
  def parse(input: List[Byte]): Parser.Result[A]
}

object Parser {

  case class Result[+A](value: A, remainder: List[Byte]) {
    def map[B](fun: A => B): Result[B] = Result[B](fun(value), remainder)
  }

  def start[A](remainder: List[Byte]): Result[List[A]] = Result(Nil, remainder)

  /**
   * transforms a list of parsers into a parser for another type
   */
  def sequence[A, B](parsers: List[Parser[A]])(combine: List[A] => B): Parser[B] = new Parser[B] {

    override def parse(input: List[Byte]): Parser.Result[B] = {
      val begin = Parser.start[A](input)

      val output = parsers.foldLeft(begin) { (state, parser) =>
        val result = parser.parse(state.remainder)
        Parser.Result(result.value :: state.value, result.remainder)
      }

      Parser.Result(combine(output.value.reverse), output.remainder)
    }
  }

  def sequence[A, B](parsers: Parser[A]*)(combine: List[A] => B): Parser[B] = sequence(parsers.toList)(combine)

}
