package co.ledger.wallet.daemon.services

import co.ledger.core.{Account, ERC20LikeOperation, Operation, Wallet}
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.models.Operations
import com.rabbitmq.client.{BuiltinExchangeType, ConnectionFactory}
import com.twitter.inject.Logging
import javax.inject.Singleton

import scala.collection.mutable
import scala.concurrent.Future
import scala.collection.JavaConverters._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import com.twitter.finatra.json.FinatraObjectMapper

@Singleton
class RabbitMQ() extends Logging {
  private val conn = {
    val factory = new ConnectionFactory
    factory.setUri(DaemonConfiguration.rabbitMQUri)
    val conn = factory.newConnection
    info(s"RabbitMQ: connected to ${conn.getAddress.toString}")
    conn
  }
  private val chan = conn.createChannel()
  private val declaredExchanges = mutable.HashSet[String]()

  // Channel is not recommended to be used concurrently, hence synchronized
  def publish(exchangeName: String, routingKeys: List[String], payload: Array[Byte]): Unit = synchronized {
    if (!declaredExchanges.contains(exchangeName)) {
      chan.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true)
      declaredExchanges += exchangeName
    }
    chan.basicPublish(exchangeName, routingKeys.mkString("."), null, payload)
  }

  private val mapper = FinatraObjectMapper.create()

  def publishOperation(op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    operationPayload(op, account, wallet).map(payload =>
      publish(poolName, getTransactionRoutingKeys(op, account, wallet.getCurrency.getName, poolName), payload)
    )
  }

  def publishERC20Operation(op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    if (account.isInstanceOfEthereumLikeAccount) {
      val erc20Accounts = account.asEthereumLikeAccount().getERC20Accounts.asScala
      val ethereumTransaction = op.asEthereumLikeOperation().getTransaction
      val senderAddress = ethereumTransaction.getSender.toEIP55
      val receiverAddress = ethereumTransaction.getReceiver.toEIP55
      Future.sequence(erc20Accounts.filter{erc20Account =>
        val contractAddress = erc20Account.getToken.getContractAddress
        contractAddress.equalsIgnoreCase(senderAddress) ||
          contractAddress.equalsIgnoreCase(receiverAddress)
      }.flatMap{ erc20Account =>
        erc20Account.getOperations.asScala
          .filter(_.getHash.equalsIgnoreCase(ethereumTransaction.getHash))
          .map{ erc20Operation =>
            publishERC20Operation(erc20Operation, op, account, wallet, poolName)
          }
      }).map(_ => Unit)
    } else {
      Future.unit
    }
  }

  def publishERC20Operation(erc20Operation: ERC20LikeOperation, op: Operation, account: Account, wallet: Wallet, poolName: String): Future[Unit] = {
    erc20OperationPayload(erc20Operation, op, account, wallet).map{ payload =>
      val routingKey = getERC20TransactionRoutingKeys(erc20Operation, account, wallet.getCurrency.getName, poolName)
      publish(poolName, routingKey, payload)
    }
  }

  private def erc20OperationPayload(erc20Operation: ERC20LikeOperation, operation: Operation, account: Account, wallet: Wallet): Future[Array[Byte]] = {
    Operations.getErc20View(erc20Operation, operation, wallet, account).map{
      mapper.writeValueAsBytes(_)
    }
  }

  private def operationPayload(op: Operation, account: Account, wallet: Wallet): Future[Array[Byte]] = {
    Operations.getView(op, wallet, account).map {
      mapper.writeValueAsBytes(_)
    }
  }

  private def getERC20TransactionRoutingKeys(erc20Op: ERC20LikeOperation, account: Account, currencyName: String, poolName: String): List[String] = {
    List(
      "transactions",
      poolName,
      currencyName,
      account.getIndex.toString,
      "erc20",
      erc20Op.getOperationType.toString.toLowerCase()
    )
  }

  private def getTransactionRoutingKeys(op: Operation, account: Account, currencyName: String, poolName: String): List[String] = {
    List(
      "transactions",
      poolName,
      currencyName,
      account.getIndex.toString,
      op.getOperationType.toString.toLowerCase
    )
  }
}
