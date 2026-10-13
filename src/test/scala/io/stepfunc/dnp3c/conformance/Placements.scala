// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ ProcedureDefinition, TestProcedure }

// where the 2.7 tree places a definition, for tests that name a definition by its number
object Placements {

  private lazy val nodes: Map[ProcedureDefinition, TestProcedure] =
    Revisions.v2_7.flatMapProcedures(p => Some(p.definition -> p)).toMap

  def of(definition: ProcedureDefinition): TestProcedure = nodes(definition)

  extension (definition: ProcedureDefinition) def id: String = of(definition).id
}
