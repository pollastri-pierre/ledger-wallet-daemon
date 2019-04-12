package co.ledger.wallet.daemon.models

import java.util.{Date, UUID}

import co.ledger.core
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.models.Wallet.RichCoreWallet
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import co.ledger.wallet.daemon.models.coins.{Bitcoin, EthereumTransactionView}
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._
import scala.concurrent.Future
/**
  * The operation related functions.
  *
  * User: Ting Tu
  * Date: 24-10-2018
  * Time: 17:29
  *
  */
object Operations {
  def confirmations(operation: core.Operation, wallet: core.Wallet): Future[Long] = {
    for {
      currentHeight <- wallet.lastBlockHeight
    } yield Option(operation.getBlockHeight) match {
      case Some(opHeight) => currentHeight - opHeight + 1
      case None => 0L
    }
  }

  def getView(operation: core.Operation, wallet: core.Wallet, account: core.Account): Future[OperationView] = {
    val height: Long = operation.getBlockHeight
    for {
      confirms <- confirmations(operation, wallet)
      curFamily = operation.getWalletType
    } yield OperationView(
      operation.getUid,
      wallet.getCurrency.getName,
      curFamily,
      Option(operation.getTrust).map(getTrustIndicatorView),
      confirms,
      operation.getDate,
      Option(height),
      operation.getOperationType,
      operation.getAmount.toString,
      operation.getFees.toString,
      wallet.getName,
      account.getIndex,
      operation.getSenders.asScala.toList,
      operation.getRecipients.asScala.toList,
      getTransactionView(operation, curFamily)
    )
  }

  def getTrustIndicatorView(indicator: core.TrustIndicator): TrustIndicatorView = {
    TrustIndicatorView(
      indicator.getTrustWeight,
      indicator.getTrustLevel,
      indicator.getConflictingOperationUids.asScala.toList,
      indicator.getOrigin)
  }

  def getTransactionView(operation: core.Operation, curFamily: core.WalletType): Option[TransactionView] = {
    if (operation.isComplete) {
      curFamily match {
        case core.WalletType.BITCOIN => Some(Bitcoin.newTransactionView(operation.asBitcoinLikeOperation().getTransaction))
        case core.WalletType.ETHEREUM => Some(EthereumTransactionView(operation.asEthereumLikeOperation().getTransaction))
        case _ => None
      }
    } else {
      None
    }
  }

  case class OperationView(
                            @JsonProperty("uid") uid: String,
                            @JsonProperty("currency_name") currencyName: String,
                            @JsonProperty("currency_family") currencyFamily: core.WalletType,
                            @JsonProperty("trust") trust: Option[TrustIndicatorView],
                            @JsonProperty("confirmations") confirmations: Long,
                            @JsonProperty("time") time: Date,
                            @JsonProperty("block_height") blockHeight: Option[Long],
                            @JsonProperty("type") opType: core.OperationType,
                            @JsonProperty("amount") amount: String,
                            @JsonProperty("fees") fees: String,
                            @JsonProperty("wallet_name") walletName: String,
                            @JsonProperty("account_index") accountIndex: Int,
                            @JsonProperty("senders") senders: Seq[String],
                            @JsonProperty("recipients") recipients: Seq[String],
                            @JsonProperty("transaction") transaction: Option[TransactionView]
                          )

  case class TrustIndicatorView(
                                 @JsonProperty("weight") weight: Int,
                                 @JsonProperty("level") level: core.TrustLevel,
                                 @JsonProperty("conflicted_operations") conflictedOps: Seq[String],
                                 @JsonProperty("origin") origin: String
                               )

  case class PackedOperationsView(
                                   @JsonProperty("previous") previous: Option[UUID],
                                   @JsonProperty("next") next: Option[UUID],
                                   @JsonProperty("operations") operations: Seq[OperationView]
                                 )
}
