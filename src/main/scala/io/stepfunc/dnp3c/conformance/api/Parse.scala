// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import java.util.regex.Pattern

object Parse {

  private def removeWhitespace(s: String): String = {
    s.replaceAll("\\s+", "")
  }

  // a comma separated list of indices and ranges, e.g. "1, 3 ... 5"
  def indexList(input: String): List[Index] = {
    if (input.trim.isEmpty) Nil
    else input.split(",", -1).toList.flatMap(indexRange)
  }

  private def indexRange(input: String): List[Index] = {
    if (input.contains("...")) {
      val items = input.split(Pattern.quote("..."), -1).map(removeWhitespace)
      if (items.length != 2) {
        throw new Exception("Bad index range: " + input)
      }

      val start = index(items.apply(0)).value
      val end = index(items.apply(1)).value

      if (end < start) {
        throw new Exception("ranges cannot run backwards: " + input)
      }

      (start to end).map(Index.apply).toList
    } else {
      List(index(removeWhitespace(input)))
    }
  }

  def index(input: String, max: Index = Index.max): Index = {
    val s = input.trim
    if (s.matches("[0-9]+") && BigInt(s) <= max.value) Index(s.toInt)
    else throw new Exception(s"not an index from 0 to ${max.value}: \"$input\"")
  }

  // "null", or nothing, for no index
  def optionalIndex(input: String, max: Index = Index.max): Option[Index] = {
    val s = input.trim
    if (s.isEmpty || s == "null") None else Some(index(s, max))
  }

  def integer(input: String): Int = {
    try java.lang.Integer.parseInt(input.trim)
    catch { case _: NumberFormatException => throw new Exception(s"not an integer: \"$input\"") }
  }

  def boolean(input: String): Boolean = input.trim.toLowerCase match {
    case "true" => true
    case "false" => false
    case _ => throw new Exception(s"not true or false: \"$input\"")
  }

  // a byte in hexadecimal, prefixed with "0x" so that a reader of the file can't take it for decimal, e.g. "0x81"
  def hexByte(input: String): Byte = {
    val s = input.trim
    if (!(s.startsWith("0x") || s.startsWith("0X")))
      throw new Exception(s"write hexadecimal values with a 0x prefix, e.g. 0x81: \"$input\"")
    val digits = s.drop(2)
    if (digits.matches("0*[0-9a-fA-F]{1,2}")) java.lang.Integer.parseInt(digits, 16).toByte
    else throw new Exception(s"not a hexadecimal byte from 0x00 to 0xFF: \"$input\"")
  }
}
