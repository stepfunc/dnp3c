// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

sealed trait ObjectType
object Static extends ObjectType
object StaticVariationZero extends ObjectType
object Event extends ObjectType
object EventVariationZero extends ObjectType
object Command extends ObjectType
object Information extends ObjectType
object CommonTimeOfOccurrence extends ObjectType
object Attribute extends ObjectType
object Unknown extends ObjectType
