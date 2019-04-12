package co.ledger.wallet.daemon.controllers

import java.util.{Date, UUID}

import co.ledger.core.{OperationType, TimePeriod}
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.CommonMethodValidations.DATE_FORMATTER
import co.ledger.wallet.daemon.controllers.requests._
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.filters.AccountCreationContext._
import co.ledger.wallet.daemon.filters.ExtendedAccountCreationContext._
import co.ledger.wallet.daemon.filters.{AccountCreationFilter, AccountExtendedCreationFilter}
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.TokenAccountInfo
import co.ledger.wallet.daemon.services.{AccountsService, OperationQueryParams}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}
import javax.inject.Inject

import scala.concurrent.ExecutionContext

class AccountsController @Inject()(accountsService: AccountsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  import AccountsController._

  prefix("/pools/:pool_name/wallets/:wallet_name") {

    // End point queries for account views with specified pool name and wallet name.
    get("/accounts") { request: AccountsRequest =>
      info(s"GET accounts $request")
      accountsService.accounts(request.walletInfo)
    }

    // End point queries for derivation information view of next account creation.
    get("/accounts/next") { request: AccountCreationInfoRequest =>
      info(s"GET account creation info $request")
      accountsService.nextAccountCreationInfo(request.account_index, request.walletInfo)
    }

    // End point to create a new account within the specified pool and wallet.
    filter[AccountCreationFilter]
      .post("/accounts") { request: AccountsRequest =>
        info(s"CREATE account $request, " +
          s"Parameters(user: ${request.user.id}, pool_name: ${request.pool_name}, wallet_name: ${request.wallet_name}), " +
          s"Body(${request.request.accountCreationBody}")
        accountsService.createAccount(request.request.accountCreationBody, request.walletInfo)
      }

    // End point to create a new account within the specified pool and wallet with extended keys info.
    filter[AccountExtendedCreationFilter]
      .post("/accounts/extended") { request: AccountsRequest =>
        info(s"CREATE account ${request.request}, " +
          s"Parameters(user: ${request.user.id}, pool_name: ${request.pool_name}, wallet_name: ${request.wallet_name}), " +
          s"Body(${request.request.accountExtendedCreationBody}")
        accountsService.createAccountWithExtendedInfo(request.request.accountExtendedCreationBody, request.walletInfo)
      }

    // End point queries for derivation information view of next account creation (with extended key).
    get("/accounts/next_extended") { request: AccountCreationInfoRequest =>
      info(s"GET account creation info $request")
      accountsService.nextExtendedAccountCreationInfo(request.account_index, request.walletInfo)
    }

    // End point queries for account view with specified pool, wallet name, and unique account index.
    prefix("/accounts/:account_index") {

      get("") { request: AccountRequest =>
        info(s"GET account $request")
        accountsService.account(request.accountInfo).map {
          case Some(view) => ResponseSerializer.serializeOk(view, response)
          case None => ResponseSerializer.serializeNotFound(Map("response" -> "Account doesn't exist", "account_index" -> request.account_index), response)
        }.recover {
          case _: WalletPoolNotFoundException => ResponseSerializer.serializeBadRequest(
            Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
            response)
          case _: WalletNotFoundException => ResponseSerializer.serializeBadRequest(
            Map("response" -> "Wallet doesn't exist", "wallet_name" -> request.wallet_name),
            response)
          case e: Throwable => ResponseSerializer.serializeInternalError(response, e)
        }
      }

      // End point queries for fresh addresses with specified pool, wallet name and unique account index.
      get("/addresses/fresh") { request: AccountRequest =>
        info(s"GET fresh addresses $request")
        accountsService.accountFreshAddresses(request.accountInfo)
      }


      // End point queries for derivation path with specified pool, wallet name and unique account index.
      get("/path") { request: AccountRequest =>
        info(s"GET account derivation path $request")
        accountsService.accountDerivationPath(request.accountInfo)
      }

      // End point queries for operation views with specified pool, wallet name, and unique account index.
      get("/operations") { request: OperationsRequest =>
        info(s"GET account operations $request")
        request.contract match {
          case Some(contract) =>
            accountsService.getERC20Operations(TokenAccountInfo(contract, request.accountInfo))
          case None =>
            accountsService.accountOperations(OperationQueryParams(request.previous, request.next, request.batch, request.full_op), request.accountInfo)
        }
      }

      // End point queries for account balance
      get("/balance") { request: BalanceRequest =>
        info(s"GET account balance $request")
        accountsService.getBalance(request.contract, request.accountInfo)
      }

      // End point queries for account xpub
      get("/xpub") { request: AccountRequest =>
        info(s"GET account xpub $request")
        accountsService.getXpub(request.accountInfo)
      }

      // End point queries for operation view with specified uid, return the first operation of this account if uid is 'first'.
      get("/operations/:uid") { request: OperationRequest =>
        info(s"GET account operation $request")
        request.uid match {
          case "first" => accountsService.firstOperation(request.accountInfo)
            .map {
              case Some(view) => ResponseSerializer.serializeOk(view, response)
              case None => ResponseSerializer.serializeNotFound(Map("response" -> "Account is empty"), response)
            }
          case _ => accountsService.accountOperation(request.uid, request.full_op, request.accountInfo)
            .map {
              case Some(view) => ResponseSerializer.serializeOk(view, response)
              case None => ResponseSerializer.serializeNotFound(Map("response" -> "Account operation doesn't exist", "uid" -> request.uid), response)
            }
        }
      }

      // Return the balances and operation counts history in the order of the starting time to the end time.
      get("/history") { request: HistoryRequest =>
        info(s"Get history $request")
        for {
          accountOpt <- accountsService.getAccount(request.accountInfo)
          account = accountOpt.getOrElse(throw AccountNotFoundException(request.account_index))
          balances <- account.balances(request.start, request.end, request.timePeriod)
          operationCounts <- account.operationsCounts(request.startDate, request.endDate, request.timePeriod)
        } yield HistoryResponse(balances, operationCounts)
      }

      // Synchronize a single account
      post("/operations/synchronize") { request: AccountRequest =>
        accountsService.synchronizeAccount(request.accountInfo)
      }

      // List of tokens on this account
      get("/tokens") { request: AccountRequest =>
        accountsService.getTokenAccounts(request.accountInfo)
      }

      // operations of all tokens on this account
      get("/tokens/operations") { request: AccountRequest =>
        accountsService.getERC20Operations(request.accountInfo)
      }

      // given token address, get the token on this account
      get("/tokens/:token_address") { request: TokenAccountRequest =>
        accountsService.getTokenAccount(request.tokenAccountInfo)
      }

      // given token address, get the operations on this token
      get("/tokens/:token_address/operations") { request: TokenAccountRequest =>
        accountsService.getERC20Operations(request.tokenAccountInfo)
      }
    }
  }

}

object AccountsController {
  private val DEFAULT_BATCH: Int = 20
  private val DEFAULT_OPERATION_MODE: Int = 0


  abstract class BaseAccountRequest extends RequestWithUser with WithWalletInfo {
    val pool_name: String
    val wallet_name: String

    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)
  }

  abstract class BaseSingleAccountRequest extends BaseAccountRequest with WithAccountInfo {
    val account_index: Int

    @MethodValidation
    def validateAccountIndex: ValidationResult = ValidationResult.validate(account_index >= 0, "account_index: index can not be less than zero")
  }

  case class HistoryResponse(balances: List[BigInt], operationCounts: List[Map[OperationType, Int]])

  case class HistoryRequest(
                             @RouteParam override val pool_name: String,
                             @RouteParam override val wallet_name: String,
                             @RouteParam override val account_index: Int,
                             @QueryParam start: String, @QueryParam end: String, @QueryParam timeInterval: String,
                             request: Request
                           ) extends BaseSingleAccountRequest {

    def timePeriod: TimePeriod = TimePeriod.valueOf(timeInterval)

    def startDate: Date = DATE_FORMATTER.parse(start)

    def endDate: Date = DATE_FORMATTER.parse(end)

    @MethodValidation
    def validateDate: ValidationResult = CommonMethodValidations.validateDates(start, end)

    @MethodValidation
    def validateTimePeriod: ValidationResult = CommonMethodValidations.validateTimePeriod(timeInterval)
  }

  case class AccountRequest(
                             @RouteParam override val pool_name: String,
                             @RouteParam override val wallet_name: String,
                             @RouteParam override val account_index: Int,
                             request: Request) extends BaseSingleAccountRequest

  case class AccountsRequest(
                              @RouteParam override val pool_name: String,
                              @RouteParam override val wallet_name: String,
                              request: Request
                            ) extends BaseAccountRequest

  case class TokenAccountRequest(
                                  @RouteParam pool_name: String,
                                  @RouteParam wallet_name: String,
                                  @RouteParam account_index: Int,
                                  @RouteParam token_address: String,
                                  request: Request
                                )
    extends BaseSingleAccountRequest with WithTokenAccountInfo

  case class AccountCreationInfoRequest(
                                         @RouteParam pool_name: String,
                                         @RouteParam wallet_name: String,
                                         @QueryParam account_index: Option[Int],
                                         request: Request
                                       ) extends RequestWithUser with WithWalletInfo {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validateAccountIndex: ValidationResult = CommonMethodValidations.validateOptionalAccountIndex(account_index)
  }

  case class OperationsRequest(
                                @RouteParam override val pool_name: String,
                                @RouteParam override val wallet_name: String,
                                @RouteParam override val account_index: Int,
                                @QueryParam next: Option[UUID],
                                @QueryParam previous: Option[UUID],
                                @QueryParam batch: Int = DEFAULT_BATCH,
                                @QueryParam full_op: Int = DEFAULT_OPERATION_MODE,
                                @QueryParam contract: Option[String],
                                request: Request
                              ) extends BaseSingleAccountRequest {

    @MethodValidation
    def validateBatch: ValidationResult = ValidationResult.validate(batch > 0, "batch: batch should be greater than zero")

  }

  case class BalanceRequest(
                             @RouteParam override val pool_name: String,
                             @RouteParam override val wallet_name: String,
                             @RouteParam override val account_index: Int,
                             // TODO find better way to handle ERC20 contract
                             @QueryParam contract: Option[String],
                             request: Request
                           ) extends BaseSingleAccountRequest

  case class OperationRequest(
                               @RouteParam override val pool_name: String,
                               @RouteParam override val wallet_name: String,
                               @RouteParam override val account_index: Int,
                               @RouteParam uid: String,
                               @QueryParam full_op: Int = 0,
                               request: Request
                             ) extends BaseSingleAccountRequest

}