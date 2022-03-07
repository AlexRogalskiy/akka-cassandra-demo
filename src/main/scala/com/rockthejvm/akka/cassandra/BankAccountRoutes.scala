package com.rockthejvm.akka.cassandra

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.headers.Location
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import com.rockthejvm.akka.cassandra.Bank.{BankAccountBalanceUpdatedResponse, BankAccountCreatedResponse, GetBankAccountResponse}
import com.rockthejvm.akka.cassandra.BankAccountRoutes.{BankAccountBalanceUpdateRequest, BankAccountCreationRequest}
import com.rockthejvm.akka.cassandra.PersistentBankAccount.{Command, CreateBankAccount, UpdateBalance}

object BankAccountRoutes {
  final case class BankAccountCreationRequest(user: String, currency: String, balance: Double)
  final case class BankAccountBalanceUpdateRequest(currency: String, amount: Double)
}

class BankAccountRoutes(bank: ActorRef[Command])(implicit val system: ActorSystem[_]) {

  implicit val ec = system.executionContext

  private implicit val timeout =
    Timeout.create(system.settings.config.getDuration("akka-cassandra-demo.routes.ask-timeout"))

  def findBankAccount(id: String): Future[GetBankAccountResponse] = ???
  def createBankAccount(
      bankAccount: BankAccountCreationRequest
  ): Future[BankAccountCreatedResponse] =
    bank.ask(replyTo =>
      CreateBankAccount(
        bankAccount.user,
        bankAccount.currency,
        bankAccount.balance,
        replyTo
      )
    )
  def updateBalance(id: String, request: BankAccountBalanceUpdateRequest): Future[Double] =
    bank.ask { replyTo: ActorRef[BankAccountBalanceUpdatedResponse] =>
      UpdateBalance(
        id,
        request.currency,
        request.amount,
        replyTo
      )
    }.map(_.newBalance)

  val bankAccountRoutes: Route =
    pathPrefix("bank-accounts") {
      concat(
        pathEnd {
          concat(post {
            entity(as[BankAccountCreationRequest]) { bankAccount =>
              onSuccess(createBankAccount(bankAccount)) { response =>
                respondWithHeader(Location(s"/bank-accounts/${response.id}")) {
                  complete(StatusCodes.Created)
                }
              }
            }
          })
        },
        path(Segment) { id =>
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(findBankAccount(id)) { response =>
                  complete(response.maybeBankAccount)
                }
              }
            },
            put {
              entity(as[BankAccountBalanceUpdateRequest]) { request =>
                onSuccess(updateBalance(id, request)) { newBalance =>
                  // FIXME: This is not the correct return type
                  complete((StatusCodes.OK, newBalance))
                }
              }
            }
          )
        }
      )
    }

}
