package com.thoughtworks.dsl.domains

import com.thoughtworks.dsl.Dsl

import scala.util.control.Exception.Catcher

/** The state for DSL in exception-handling domain.
  *
  * @author 杨博 (Yang Bo)
  */
trait ExceptionHandling[OtherDomain] { self =>
  def onFailure(handler: Throwable => OtherDomain): OtherDomain

}

object ExceptionHandling {

  implicit final class CpsCatchOps[OtherDomain](catcher: Catcher[ExceptionHandling[OtherDomain]]) {
    def cpsCatch(
        continuation: (
            ExceptionHandling[OtherDomain] => ExceptionHandling[OtherDomain]) => ExceptionHandling[OtherDomain])
      : ExceptionHandling[OtherDomain] = {
      new ExceptionHandling[OtherDomain] {
        def onFailure(failureHandler: Throwable => OtherDomain): OtherDomain = {

          object Extractor {
            def unapply(e: Throwable): Option[ExceptionHandling[OtherDomain]] = catcher.lift(e)
          }

          val safeTryResult: ExceptionHandling[OtherDomain] = try {
            continuation { domain =>
              ExceptionHandling.success(domain.onFailure(failureHandler))
            }
          } catch {
            case Extractor(handled) => return handled.onFailure(failureHandler)
            case e: Throwable       => return failureHandler(e)
          }

          safeTryResult.onFailure {
            case Extractor(handled) => handled.onFailure(failureHandler)
            case e: Throwable       => failureHandler(e)
          }
        }
      }
    }
  }

  def success[Domain](r: Domain): ExceptionHandling[Domain] = new ExceptionHandling[Domain] {
    def onFailure(handler: Throwable => Domain): Domain = r
  }

  def failure[Domain](e: Throwable): ExceptionHandling[Domain] = new ExceptionHandling[Domain] {
    def onFailure(handler: Throwable => Domain): Domain = handler(e)
  }

  implicit def mayFailDsl[Instruction, Domain, A](
      implicit restDsl: Dsl[Instruction, Domain, A]): Dsl[Instruction, ExceptionHandling[Domain], A] =
    new Dsl[Instruction, ExceptionHandling[Domain], A] {
      def interpret(instruction: Instruction,
                    successHandler: A => ExceptionHandling[Domain]): ExceptionHandling[Domain] =
        new ExceptionHandling[Domain] {
          def onFailure(failureHandler: Throwable => Domain): Domain = {
            def restHandler(a: A): Domain =
              (try {
                successHandler(a)
              } catch {
                case e: Throwable =>
                  return failureHandler(e)
              }).onFailure(failureHandler)

            restDsl.interpret(instruction, restHandler)
          }
        }

    }

}
