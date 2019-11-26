package co.ledger.wallet.daemon.libledger_core.async

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, LinkedBlockingQueue, ThreadFactory, ThreadPoolExecutor, TimeUnit}
import java.util.{Timer, TimerTask}

import co.ledger.core
import co.ledger.wallet.daemon.async.SerialExecutionContextWrapper

import scala.concurrent.{ExecutionContext, Future}

class LedgerCoreExecutionContext(ec: ExecutionContext) extends co.ledger.core.ExecutionContext {
  private implicit val context: ExecutionContext = ec

  override def execute(runnable: core.Runnable): Unit = Future {
    runnable.run()
  }

  override def delay(runnable: core.Runnable, millis: Long): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = execute(runnable)
    }, millis)
  }

}

object LedgerCoreExecutionContext {
  def apply(ec: ExecutionContext): LedgerCoreExecutionContext = new LedgerCoreExecutionContext(ec)

  def newThreadPool(prefix: String): LedgerCoreExecutionContext = apply(ExecutionContext.fromExecutor(
      new ThreadPoolExecutor(0, Runtime.getRuntime.availableProcessors() * 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable],
        new ThreadFactory {
          val counter: AtomicInteger = new AtomicInteger
          override def newThread(r: Runnable): Thread = new Thread(r, s"CorePool-${counter.incrementAndGet}-$prefix")
        })
    ))

  def newSerialQueue(prefix: String): LedgerCoreExecutionContext = apply(
    new SerialExecutionContextWrapper(
      ExecutionContext.fromExecutorService(
        new ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable],
          new ThreadFactory {
            override def newThread(r: Runnable): Thread = new Thread(r, s"Serial-CorePool-$prefix")
          }
        )
      )
    ))
}
