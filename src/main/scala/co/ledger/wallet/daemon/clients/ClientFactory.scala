package co.ledger.wallet.daemon.clients

import java.util.concurrent.{Executors, LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import co.ledger.wallet.daemon.libledger_core.async.{LedgerCoreExecutionContext, ScalaThreadDispatcher}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext

object ClientFactory {
  private val config = ConfigFactory.load()

  lazy val webSocketClient = new ScalaWebSocketClient
  lazy val httpCoreClient = new HttpCoreClientPool(LedgerCoreExecutionContext.httpPool, new ScalaHttpClientPool, config.getInt("core.http_client_retries"))

  lazy val threadDispatcher = new ScalaThreadDispatcher(
    ExecutionContext.fromExecutor(new ThreadPoolExecutor(Runtime.getRuntime.availableProcessors() * 4, Runtime.getRuntime.availableProcessors() * 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable], (r: Runnable) => new Thread(r, "libcore-thread-dispatcher")))
  )

  lazy val apiClient: ApiClient = new ApiClient()(
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool((r: Runnable) => new Thread(r, "api-client")))
  )

}
