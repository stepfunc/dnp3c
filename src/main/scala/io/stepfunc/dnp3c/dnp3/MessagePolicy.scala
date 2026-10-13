// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app.Message

/**
 * A rule that a revision of the test procedures applies to every application message a procedure receives, e.g. that
 * a response requests confirmation only when the protocol requires it. An expectation the rule doesn't fit, e.g. one
 * that checks a confirmation request on purpose, exempts it with [[Policies.exempting]] around exactly the reads it
 * makes; a deferred check that reads messages does the same, since it runs after its step.
 */
trait MessagePolicy {

  /** a short name, which a violation and an exemption name it by */
  def id: String

  /** what the rule requires */
  def description: String

  /** what's wrong with the message, if it breaks the rule */
  def violation(message: Message): Option[String]
}

/**
 * The rules a procedure's messages must follow, and the ones the running step exempts. A procedure gets its own,
 * since what's exempt changes as its steps run.
 */
final class Policies(val all: List[MessagePolicy]) {

  private var exempt: Set[String] = Set.empty

  /**
   * Check a received message against every rule that isn't exempt
   *
   * @throws Exception naming the rule it breaks, which fails the step
   */
  def check(message: Message): Unit =
    for (policy <- all if !exempt.contains(policy.id); problem <- policy.violation(message))
      throw new Exception(s"The response breaks the rule ${policy.id} (${policy.description}): $problem")

  /** run f with the rules named exempt, e.g. for the read of a response a step expects to break one */
  def exempting[T](ids: Set[String])(f: => T): T = {
    val before = exempt
    exempt = exempt ++ ids
    try f
    finally exempt = before
  }
}

object Policies {

  /** no rules: every message is fine */
  def none: Policies = new Policies(Nil)
}
