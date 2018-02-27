package com.thoughtworks.dsl.instructions

import com.thoughtworks.dsl.Dsl
import com.thoughtworks.dsl.Dsl.Instruction
import com.thoughtworks.dsl.domains.{ExceptionHandling, Scope}
import resource.Resource

/**
  * @author 杨博 (Yang Bo)
  */
final case class Arm[R](resourceFactory: () => R, resource: Resource[R]) extends Instruction[Arm[R], R]

object Arm {

  def apply[R](r: => R)(implicit resource: Resource[R], dummyImplicit: DummyImplicit = DummyImplicit.dummyImplicit) = {
    new Arm[R](r _, resource)
  }

  implicit def armContinuationDsl[Domain, R, A](
      implicit dsl: com.thoughtworks.dsl.Dsl[com.thoughtworks.dsl.instructions.Catch[Domain], Domain, Domain => Domain])
    : Dsl[Arm[R], ((A => Domain) => Domain), R] =
    new Dsl[Arm[R], ((A => Domain) => Domain), R] {
      def interpret(arm: Arm[R], inUse: R => ((A => Domain) => Domain)): ((A => Domain) => Domain) = _ {
        val Arm(resourceFactory, resource) = arm
        val r = resourceFactory()
        try {
          resource.open(r)
          !Shift(inUse(r))
        } finally {
          resource.close(r)
        }
      }
    }

  implicit def armDsl[Domain, R](
      implicit dsl: com.thoughtworks.dsl.Dsl[com.thoughtworks.dsl.instructions.Catch[Domain], Domain, Domain => Domain])
    : Dsl[Arm[R], Scope[Domain], R] = new Dsl[Arm[R], Scope[Domain], R] {
    def interpret(arm: Arm[R], inUse: R => Scope[Domain]): Scope[Domain] = {
      val resourceFactory = arm.resourceFactory
      val resource = arm.resource

      new Scope[Domain] {
        def apply(continue: Domain => Domain): Domain = {
          continue {
            val r = resourceFactory()
            try {
              resource.open(r)
              !Shift(inUse(r))
            } finally {
              resource.close(r)
            }
          }
        }
      }
    }
  }

}