package co.ledger.wallet.daemon.context

import java.util.concurrent.{Executors, ForkJoinPool, ForkJoinWorkerThread}

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import com.twitter.concurrent.NamedPoolThreadFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object ApplicationContext {

  /**
    * Execution Context dedicated for waiting synchronization
    */
  implicit val IOPool: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(new NamedPoolThreadFactory("walletdaemon-IO")))

  /**
    * Libcore thread dispatcher context
    */
  val libcoreEc: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2 * Runtime.getRuntime.availableProcessors(),
      new NamedPoolThreadFactory("libcore-dispatcher")))

  private val forkJoinWorkerThreadFactory: (String) => ForkJoinPool.ForkJoinWorkerThreadFactory = (prefix: String) => new ForkJoinPool.ForkJoinWorkerThreadFactory() {
    override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
      val worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
      worker.setName(prefix + worker.getPoolIndex)
      worker
    }
  }

  /**
    * Libcore http call ExcutionContext
    */
  val httpEc: ExecutionContext = ExecutionContext.fromExecutorService(
    new ForkJoinPool(Runtime.getRuntime.availableProcessors(), forkJoinWorkerThreadFactory("libcore-http"), null, false)
  )

  /**
    * Libcore http call ExcutionContext
    */
  val coreCpuEc: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(DaemonConfiguration.corePoolOpSizeFactor * Runtime.getRuntime.availableProcessors(),
      new NamedPoolThreadFactory("libcore-cpu")))

}
