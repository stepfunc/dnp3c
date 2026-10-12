// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

sealed trait ItemIndex
object NotIndexed extends ItemIndex
case class Indexed(idx: Int) extends ItemIndex

case class IndexedPayloadItem(idx: ItemIndex, point: Point)
object IndexedPayloadItem {
  def apply(idx: Int, point: Point) = new IndexedPayloadItem(Indexed(idx), point)
  def apply(point: Point) = new IndexedPayloadItem(NotIndexed, point)
}
