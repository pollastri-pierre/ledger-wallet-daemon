package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.clients.ClientFactory
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.PoolDto
import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, WalletNotFoundException}
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.{HexUtils, Utils}
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging
import org.bitcoinj.core.Sha256Hash
import Wallet._
import co.ledger.core.ErrorCode
import co.ledger.core.ConfigurationDefaults
import co.ledger.wallet.daemon.libledger_core.database.JDBCDatabaseEngine
import slick.jdbc.JdbcDataSource

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.{ExecutionContext, Future}

class Pool(private val coreP: core.WalletPool, val id: Long) extends Logging {
  private[this] val self = this

  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  private val _coreExecutionContext = LedgerCoreExecutionContext.newThreadPool()
  private[this] val eventReceivers: mutable.Set[core.EventReceiver] = Utils.newConcurrentSet[core.EventReceiver]

  val name: String = coreP.getName

  def view: Future[WalletPoolView] = coreP.getWalletCount().map { count => WalletPoolView(name, count) }

  /**
    * Obtain wallets by batch size and offset.
    *
    * @param offset the offset the query starts from.
    * @param batch  the size of query.
    * @return a tuple of total wallet count and a sequence of wallets from offset to batch size.
    */
  def wallets(offset: Int, batch: Int): Future[(Int, Seq[core.Wallet])] = {
    assert(offset >= 0, s"offset invalid $offset")
    assert(batch > 0, "batch must be positive")
    coreP.getWalletCount().flatMap { count =>
      val size = batch min (count - offset)
      coreP.getWallets(offset, size).map { coreWs =>
        coreWs.asScala.toList
      }.map((count, _))
    }
  }

  /**
    * Obtain ALL wallets.
    *
    * @return a sequence of wallet
    */
  def wallets: Future[Seq[core.Wallet]] = {
    coreP.getWalletCount().flatMap { count =>
      val batch = 20
      def walletsFromOffset(offset: Int): Future[List[core.Wallet]] = {
        val size = Math.min(batch, count - offset)
        if (size <= 0) {
          Future.successful(List.empty[core.Wallet])
        } else {
          for {
            fetchWallets <- coreP.getWallets(offset, size).map(_.asScala.toList)
            nextWallets <- walletsFromOffset(offset + batch)
          } yield fetchWallets ++ nextWallets
        }
      }
      walletsFromOffset(0)
    }
  }

  private def startListen(wallet: core.Wallet): Future[core.Wallet] = {
    for {
      _ <- wallet.startCacheAndRealTimeObserver
    } yield wallet
  }

  /**
    * Obtain wallet by name. If the name doesn't exist in local cache, a core retrieval will be performed.
    *
    * @param walletName name of wallet.
    * @return a future of optional wallet.
    */
  def wallet(walletName: String): Future[Option[core.Wallet]] = {
    coreP.getWallet(walletName).map(Option(_)).recover {
      case _: co.ledger.core.implicits.WalletNotFoundException => None
    }
  }

  /**
    * Obtain currency by name.
    *
    * @param currencyName the specified currency name.
    * @return a future of optional currency.
    */
  def currency(currencyName: String): Future[Option[core.Currency]] =
    coreP.getCurrency(currencyName).map(Option.apply).recover {
      case _: core.implicits.CurrencyNotFoundException => None
    }

  /**
    * Obtain currencies.
    *
    * @return future of currencies sequence.
    */
  def currencies(): Future[Seq[core.Currency]] = {
    coreP.getCurrencies().map(_.asScala.toList)
  }

  /**
    * Clear the event receivers on this pool and underlying wallets. It will also call `stopRealTimeObserver` method.
    *
    * @return a future of Unit.
    */
  def clear: Future[Unit] = {
    Future.successful(stopRealTimeObserver()).map { _ =>
      unregisterEventReceivers()
    }
  }

  def addWalletIfNotExist(walletName: String, currencyName: String): Future[core.Wallet] = {
    coreP.getCurrencies().foreach({currencies =>
      println("GOT CURRENCIES")
      currencies.asScala.foreach(c => println(c.getName))
      println("\\GOT CURRENCIES")
    })
    coreP.getCurrency(currencyName).flatMap { coreC =>
      coreP.createWallet(walletName, coreC, buildWalletConfig(currencyName)).flatMap { coreW =>
        info(LogMsgMaker.newInstance("Wallet created").append("name", walletName).append("pool_name", name).append("currency_name", currencyName).toString())
        startListen(coreW)
      }.recoverWith {
        case _: WalletAlreadyExistsException =>
          warn(LogMsgMaker.newInstance("Wallet already exist")
            .append("name", walletName)
            .append("pool_name", name)
            .append("currency_name", currencyName)
            .toString())
          coreP.getWallet(walletName).flatMap { coreW => startListen(coreW) }
      }
    }.recoverWith {
      case _: core.implicits.CurrencyNotFoundException => Future.failed(CurrencyNotFoundException(currencyName))
    }
  }

  def updateWalletConfig(wallet: core.Wallet): Future[core.Wallet] = {
    val walletConfig = buildWalletConfig(wallet.getCurrency.getName)
    info(LogMsgMaker.newInstance("Updating wallet")
      .append("pool_name", name)
      .append("wallet_name", wallet.getName)
      .append("api_endpoint", walletConfig.getString("BLOCKCHAIN_EXPLORER_API_ENDPOINT"))
      .append("ws_endpoint", walletConfig.getString("BLOCKCHAIN_OBSERVER_WS_ENDPOINT"))
      .toString())
    coreP.updateWalletConfig(wallet.getName, walletConfig) flatMap { result =>
      info(LogMsgMaker.newInstance("Wallet update result")
        .append("pool_name", name)
        .append("wallet_name", wallet.getName)
        .append("result", result)
        .toString())
      result match {
        case ErrorCode.FUTURE_WAS_SUCCESSFULL => Future.successful(wallet)
        case _ => Future.failed(WalletNotFoundException(wallet.getName))
      }
    }
  }

  /**
    * Subscribe specied event receiver to core pool, also save the event receiver to the local container.
    *
    * @param eventReceiver the event receiver object need to be registered.
    */
  def registerEventReceiver(eventReceiver: core.EventReceiver): Unit = {
    if (!eventReceivers.contains(eventReceiver)) {
      eventReceivers += eventReceiver
      coreP.getEventBus.subscribe(_coreExecutionContext, eventReceiver)
      debug(s"Register $eventReceiver")
    } else {
      debug(s"Already registered $eventReceiver")
    }
  }

  /**
    * Unsubscribe all event receivers for this pool, including empty the event receivers container in memory.
    *
    */
  def unregisterEventReceivers(): Unit = {
    eventReceivers.foreach { eventReceiver =>
      coreP.getEventBus.unsubscribe(eventReceiver)
      eventReceivers.remove(eventReceiver)
      debug(s"Unregister $eventReceiver")
    }
  }

  /**
    * Synchronize all accounts within this pool.
    *
    * @return a future of squence of synchronization results.
    */
  def sync(): Future[Seq[SynchronizationResult]] = {
    for {
      count <- coreP.getWalletCount()
      wallets <- coreP.getWallets(0, count)
      result <- Future.sequence(wallets.asScala.map { wallet => wallet.syncAccounts(name) }).map(_.flatten)
    } yield result
  }

  /**
    * Start real time observer of this pool will start the observers of the underlying wallets and accounts.
    *
    * @return a future of Unit.
    */
  def startRealTimeObserver(): Future[Unit] = {
    coreP.getWalletCount().map { count =>
      coreP.getWallets(0, count).map { coreWs =>
        coreWs.asScala.map { coreW => startListen(coreW) }
      }
    }
  }

  /**
    * Stop real time observer of this pool will stop the observers of the underlying wallets and accounts.
    *
    * @return a Unit.
    */
  def stopRealTimeObserver(): Unit = {
    debug(LogMsgMaker.newInstance("Stop real time observer").append("pool", name).toString())
    coreP.getWalletCount().map { count =>
      coreP.getWallets(0, count).map { coreWs =>
        coreWs.asScala.foreach { coreW => coreW.stopRealTimeObserver }
      }
    }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Pool => that.isInstanceOf[Pool] && self.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    self.id.hashCode() + self.name.hashCode
  }

  override def toString: String = s"Pool(name: $name, id: $id)"

  private def buildWalletConfig(currencyName: String): core.DynamicObject = {
    val walletConfig = core.DynamicObject.newInstance()
    val apiUrl = DaemonConfiguration.explorer.api.paths.get(currencyName) match {
      case Some(path) => path.host
      case None => ConfigurationDefaults.BLOCKCHAIN_DEFAULT_API_ENDPOINT
    }
    walletConfig.putString("BLOCKCHAIN_EXPLORER_API_ENDPOINT", apiUrl)
    val wsUrl = DaemonConfiguration.explorer.ws.getOrElse(currencyName, DaemonConfiguration.explorer.ws("default"))
    walletConfig.putString("BLOCKCHAIN_OBSERVER_WS_ENDPOINT", wsUrl)
    walletConfig
  }
}

object Pool {
  def newInstance(coreP: core.WalletPool, id: Long): Pool = {
    new Pool(coreP, id)
  }

  def newCoreInstance(source: JdbcDataSource, poolDto: PoolDto): Future[core.WalletPool] = {
    val poolConfig = core.DynamicObject.newInstance()
    //    poolConfig.putString("BLOCKCHAIN_OBSERVER_WS_ENDPOINT", "ws://notification.explorers.dev.aws.ledger.fr:9000/ws/{}")
    //    poolConfig.putString("BLOCKCHAIN_OBSERVER_ENGINE", "LEDGER_API")
    core.WalletPoolBuilder.createInstance()
      .setHttpClient(ClientFactory.httpClient)
      .setWebsocketClient(ClientFactory.webSocketClient)
      .setLogPrinter(new NoOpLogPrinter(ClientFactory.threadDispatcher.getMainExecutionContext, true))
      .setThreadDispatcher(ClientFactory.threadDispatcher)
      .setPathResolver(new ScalaPathResolver(corePoolId(poolDto.userId, poolDto.name)))
      .setRandomNumberGenerator(new SecureRandomRNG)
      .setDatabaseBackend(core.DatabaseBackend.getSqlite3Backend)
      .setConfiguration(poolConfig)
      .setName(poolDto.name)
      .setDatabaseBackend(core.DatabaseBackend.createBackendFromEngine(new JDBCDatabaseEngine(source)))
      .build()
  }

  private def corePoolId(userId: Long, poolName: String): String = HexUtils.valueOf(Sha256Hash.hash(s"$userId:$poolName".getBytes))
}

case class WalletPoolView(
                           @JsonProperty("name") name: String,
                           @JsonProperty("wallet_count") walletCount: Int
                         )

