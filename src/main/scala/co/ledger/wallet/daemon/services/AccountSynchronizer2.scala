package co.ledger.wallet.daemon.services

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, UnboundedStash}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import cats.implicits._
import co.ledger.core._
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models.{AccountInfo, Pool, PoolInfo}
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.AccountOperationsPublisher.PoolName
import co.ledger.wallet.daemon.services.AccountSynchronizer2._
import co.ledger.wallet.daemon.services.AccountSynchronizerWatchdog.{BlockHeight, GetStatus, GetStatuses, RegisterAccount}
import co.ledger.wallet.daemon.utils.AkkaUtils
import com.twitter.util.{Duration, Timer}
import javax.inject.{Inject, Singleton}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Success
import scala.util.control.NonFatal

/**
  * This module is responsible to maintain account updated
  * It's pluggable to external trigger
  *
  * @param scheduler used to schedule all the operations in ASM
  */
@Singleton
class AccountSynchronizerManager2 @Inject()(daemonCache: DaemonCache, actorSystem: ActorSystem, scheduler: Timer)
  extends DaemonService {
  def syncAccount(_accountInfo: AccountInfo): Future[SynchronizationResult] = _accountInfo match {
    case _ => Future.successful(null)
  }

  def resyncAccount(_accountInfo: AccountInfo): Unit = _accountInfo match {
    case _ => ()
  }

  def syncPool(_poolInfo: PoolInfo): Future[Seq[SynchronizationResult]] = _poolInfo match {
    case _ => Future.successful(null)
  }

  def syncAllRegisteredAccounts(): Future[Seq[SynchronizationResult]] = {
    Future.successful(null)
  }

  def unregisterPool(_p: Pool, _poolInfo: PoolInfo): Future[Unit] = (_p, _poolInfo) match {
    case _ => Future.unit
  }

  import co.ledger.wallet.daemon.context.ApplicationContext.IOPool
  import com.twitter.util.Duration

  // When we start ASM, we register the existing accountsSuccess
  // We periodically try to register account just in case there is new account created
  lazy private val periodicRegisterAccount =
  scheduler.schedule(Duration.fromSeconds(DaemonConfiguration.Synchronization.syncAccountRegisterInterval))(registerAccounts)

  lazy private val synchronizer: ActorRef = actorSystem.actorOf(
    Props(new AccountSynchronizerWatchdog(scheduler))
      .withDispatcher(SynchronizationDispatcher2.configurationKey(SynchronizationDispatcher2.Synchronizer))
  )

  // should be called after the instantiation of this class
  def start(): Future[Unit] = {
    registerAccounts.andThen {
      case Success(_) =>
        periodicRegisterAccount
        info("Started account synchronizer manager")
    }
  }

  def registerAccount(wallet: Wallet, account: Account, accountInfo: AccountInfo): Unit = this.synchronized {
    synchronizer ! RegisterAccount(wallet, account, accountInfo)
  }

  // return None if account info not found
  def getSyncStatus(accountInfo: AccountInfo): Future[Option[SyncStatus]] = {
    ask(synchronizer, GetStatus(accountInfo))(Timeout(10 seconds)).mapTo[Option[SyncStatus]]
  }

  def ongoingSyncs(): Future[List[(AccountInfo, SyncStatus)]] = {
    ask(synchronizer, GetStatuses)(Timeout(10 seconds)).mapTo[List[(AccountInfo, SyncStatus)]]
  }

  // Maybe to be called periodically to discover new account
  private def registerAccounts: Future[Unit] = {
    for {
      pools <- daemonCache.getAllPools
      wallets <- Future.sequence(pools.map { pool => pool.wallets.map(_.map(w => (pool, w))) }).map(_.flatten)
      accounts <- Future.sequence(wallets.map { case (pool, wallet) =>
        for {
          accounts <- wallet.accounts
        } yield accounts.map(account => (pool, wallet, account))
      }).map(_.flatten)
    } yield {
      accounts.foreach {
        case (pool, wallet, account) =>
          val accountInfo = AccountInfo(
            walletName = wallet.getName,
            poolName = pool.name,
            accountIndex = account.getIndex
          )
          registerAccount(wallet, account, accountInfo)
      }
    }
  }

  def close(after: Duration): Unit = {
    periodicRegisterAccount.cancel()
    periodicRegisterAccount.close(after)
    synchronizer ! PoisonPill
  }
}

class AccountSynchronizerWatchdog(scheduler: Timer) extends Actor with ActorLogging {
  implicit val ec: ExecutionContext = context.dispatcher

  case class AccountData(account: Account, wallet: Wallet, syncStatus: SyncStatus)

  val accounts: mutable.HashMap[AccountInfo, AccountData] = new mutable.HashMap[AccountInfo, AccountData]()

  var synchronizer: ActorRef = ActorRef.noSender

  override def preStart(): Unit = {
    super.preStart()
    synchronizer = context.actorOf(Props(new AccountSynchronizer2(self)), "synchronizer")
  }

  override val receive: Receive = {
    case GetStatus(account) =>
      getStatus(account).pipeTo(sender())
    case GetStatuses =>
      getStatuses.pipeTo(sender())
    case RegisterAccount(wallet, account, accountInfo) =>
      registerAccount(wallet, account, accountInfo)
    case SyncStarted(accountInfo) =>
      handleSyncStarted(accountInfo)
    case SyncSuccess(accountInfo) =>
      handleSyncSuccess(accountInfo)
    case SyncFailure(accountInfo, reason) =>
      handleSyncFailure(accountInfo, reason)
  }

  private def handleSyncStarted(accountInfo: AccountInfo): Unit = {
    val syncing = accounts(accountInfo).syncStatus match {
      case Synced(atHeight) => Syncing(atHeight, atHeight)
      case FailedToSync(_) => Syncing(0, 0)
      case other =>
        log.warning(s"Unexpected previous account synchronization state ${other} before starting new synchronization. Keep status as is, although synchronization is running.")
        other
    }
    log.debug(s"Updated new status of account ${accountInfo}: ${syncing}")
    accounts += ((accountInfo, accounts(accountInfo).copy(syncStatus = syncing)))
  }

  private def handleSyncFailure(accountInfo: AccountInfo, reason: String) = {
    val accountData = accounts(accountInfo)

    lastAccountBlockHeight(accountData.account).andThen {
      case Success(blockHeight) =>
        log.warning(s"Failed syncing account ${accountInfo}: ${reason}")
        accounts += ((accountInfo, accountData.copy(syncStatus = Synced(blockHeight.value))))
        scheduler.doLater(Duration.fromSeconds(DaemonConfiguration.Synchronization.syncInterval)) {
          synchronizer ! StartSynchronization(accountData.account, accountInfo)
        }
      case scala.util.Failure(_) => // TODO handle this error
    }
  }

  private def handleSyncSuccess(accountInfo: AccountInfo) = {
    val accountData = accounts(accountInfo)

    lastAccountBlockHeight(accountData.account).andThen {
      case Success(blockHeight) =>
        accounts += ((accountInfo, accountData.copy(syncStatus = Synced(blockHeight.value))))
        log.debug(s"New status for account ${accountInfo}: ${accounts(accountInfo).syncStatus}")

        scheduler.doLater(Duration.fromSeconds(DaemonConfiguration.Synchronization.syncInterval)) {
          synchronizer ! StartSynchronization(accountData.account, accountInfo)
        }
      case scala.util.Failure(_) => // TODO handle this error
    }
  }

  private def registerAccount(wallet: Wallet, account: Account, accountInfo: AccountInfo): Unit = {
    if (!accounts.contains(accountInfo)) {
      accounts += ((accountInfo, AccountData(account = account, wallet = wallet, syncStatus = Synced(0))))
      synchronizer ! StartSynchronization(account, accountInfo)
    }
  }

  private def getStatuses: Future[List[(AccountInfo, SyncStatus)]] = {
    Future.sequence(accounts.map(e =>
      getStatusForExistingAccount(e._1)(e._2).map((e._1, _))
    ).toList)
  }

  private def getStatus(accountInfo: AccountInfo): Future[Option[SyncStatus]] = accounts.get(accountInfo).map {
    getStatusForExistingAccount(accountInfo)
  }.sequence

  private def getStatusForExistingAccount(accountInfo: AccountInfo)(accountData: AccountData) = {
    accountData.syncStatus match {
      case Syncing(fromHeight, _) => lastAccountBlockHeight(accountData.account).map(blockHeight => {
        val newStatus = Syncing(fromHeight, blockHeight.value)
        accounts += ((accountInfo, accountData.copy(syncStatus = newStatus)))
        newStatus
      })
      case otherStatus => Future.successful(otherStatus)
    }
  }

  private def lastAccountBlockHeight(account: Account): Future[BlockHeight] = {
    import co.ledger.wallet.daemon.context.ApplicationContext.IOPool

    account.getLastBlock()
      .map(h => BlockHeight(h.getHeight))(IOPool)
      .recover {
        case _: co.ledger.core.implicits.BlockNotFoundException => BlockHeight(0)
      }(IOPool)
  }

}

object AccountSynchronizerWatchdog {

  /**
    * Gives the status @SyncStatus of the synchronization of the account
    */
  case class GetStatus(account: AccountInfo)

  case object GetStatuses

  // TODO later
  case object ForceSynchronization2

  case class RegisterAccount(wallet: Wallet, account: Account, accountInfo: AccountInfo)

  private case class BlockHeight(value: Long) extends AnyVal

}

class AccountSynchronizer2(watchdog: ActorRef) extends Actor with ActorLogging with UnboundedStash {

  implicit val ec: ExecutionContext = context.dispatcher
  var synchronizationTickets: Integer = DaemonConfiguration.Synchronization.maxOnGoing
  val queue: mutable.Queue[(Account, AccountInfo)] = new mutable.Queue[(Account, AccountInfo)]

  override def preStart(): Unit = {
    super.preStart()
  }

  override val receive: Receive = {
    case StartSynchronization(account, accountInfo) =>
      if (synchronizationTickets == 0) {
        queue += ((account, accountInfo))
      } else {
        synchronizationTickets -= 1
        sync(account, accountInfo).pipeTo(self)
        sender() ! SyncStarted(accountInfo)
      }
    case SyncSuccess(accountInfo) =>
      synchronizationTickets += 1
      watchdog ! SyncSuccess(accountInfo)
      if (queue.nonEmpty) {
        val (account, accountInfo) = queue.dequeue()
        self ! StartSynchronization(account, accountInfo)
      }
    case SyncFailure(accountInfo, reason) =>
      synchronizationTickets += 1
      watchdog ! SyncFailure(accountInfo, reason)
      if (queue.nonEmpty) {
        val (account, accountInfo) = queue.dequeue()
        self ! StartSynchronization(account, accountInfo)
      }
  }

  private def sync(account: Account, accountInfo: AccountInfo): Future[SyncResult] = {
    import co.ledger.wallet.daemon.context.ApplicationContext.IOPool
    val accountUrl: String = s"${accountInfo.poolName}/${accountInfo.walletName}/${account.getIndex}"

    log.info(s"[${self.path}]#Sync : start syncing $accountInfo")
    account.sync(accountInfo.poolName, accountInfo.walletName)(IOPool)
      .map { result =>
        if (result.syncResult) {
          log.info(s"#[${self.path}]Sync : $accountUrl has been synced : $result")
          SyncSuccess(accountInfo)
        } else {
          log.error(s"#Sync : $accountUrl has FAILED")
          SyncFailure(accountInfo, s"#Sync : Lib core failed to sync the account $accountUrl")
        }
      }(IOPool)
      .recoverWith { case NonFatal(t) =>
        log.error(t, s"#Sync Failed to sync account: $accountUrl")
        Future.successful(SyncFailure(accountInfo, t.getMessage))
      }(IOPool)
  }
}

object AccountSynchronizer2 {

  case class StartSynchronization(account: Account, accountInfo: AccountInfo)

  case class SyncStarted(accountInfo: AccountInfo)

  case object TryStartNext

  sealed trait SyncResult

  case class SyncSuccess(accountInfo: AccountInfo) extends SyncResult

  case class SyncFailure(accountInfo: AccountInfo, reason: String) extends SyncResult

  def name(account: Account, wallet: Wallet, poolName: PoolName): String = AkkaUtils.validActorName("account-synchronizer", poolName.name, wallet.getName, account.getIndex.toString)
}


sealed trait SynchronizationDispatcher2

object SynchronizationDispatcher2 {

  object LibcoreLookup extends SynchronizationDispatcher2

  object Synchronizer extends SynchronizationDispatcher2

  object Publisher extends SynchronizationDispatcher2

  type DispatcherKey = String

  def configurationKey(dispatcher: SynchronizationDispatcher2): DispatcherKey = dispatcher match {
    case LibcoreLookup => "synchronization.libcore-lookup.dispatcher"
    case Synchronizer => "synchronization.synchronizer.dispatcher"
    case Publisher => "synchronization.publisher.dispatcher"
  }

}


