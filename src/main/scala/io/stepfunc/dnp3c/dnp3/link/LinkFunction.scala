// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

case class LinkFunction(id: LinkFunction.Id, description: String)

object LinkFunction {

  case class Id(value: Byte, pri: Boolean)

  // primary

  val RESET_LINK_STATES = LinkFunction(Id(0.toByte, true), "RESET_LINK_STATES")
  val TEST_LINK_STATES = LinkFunction(Id(2.toByte, true), "TEST_LINK_STATES")
  val CONFIRMED_USER_DATA = LinkFunction(Id(3.toByte, true), "CONFIRMED_USER_DATA")
  val UNCONFIRMED_USER_DATA = LinkFunction(Id(4.toByte, true), "UNCONFIRMED_USER_DATA")
  val REQUEST_LINK_STATES = LinkFunction(Id(9.toByte, true), "REQUEST_LINK_STATES")

  // secondary

  val ACK = LinkFunction(Id(0.toByte, false), "ACK")
  val NACK = LinkFunction(Id(1.toByte, false), "NACK")
  val LINK_STATUS = LinkFunction(Id(0x0B.toByte, false), "LINK_STATUS")
  val NOT_SUPPORTED = LinkFunction(Id(0x0F.toByte, false), "NOT_SUPPORTED")

  val list: List[LinkFunction] = List(
    RESET_LINK_STATES,
    TEST_LINK_STATES,
    CONFIRMED_USER_DATA,
    UNCONFIRMED_USER_DATA,
    REQUEST_LINK_STATES,
    ACK,
    NACK,
    LINK_STATUS,
    NOT_SUPPORTED
  )

  def unknown(id: Id): LinkFunction = LinkFunction(id, "Unknown")

  def unknown(pri: Boolean, value: Byte): LinkFunction = unknown(Id(value, pri))

  private val map: Map[LinkFunction.Id, LinkFunction] = list.map(x => (x.id, x)).toMap

  def get(pri: Boolean, value: Byte): LinkFunction = {
    val id = Id(value, pri)
    map.getOrElse(id, unknown(id))
  }

}
