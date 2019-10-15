package co.ledger.wallet.daemon.libledger_core.async

import java.util.concurrent.Executors
import java.util.{Timer, TimerTask}

import co.ledger.core
import co.ledger.wallet.daemon.async.SerialExecutionContext

import scala.concurrent.{ExecutionContext, Future}

class LedgerCoreExecutionContext(ec: ExecutionContext) extends co.ledger.core.ExecutionContext {
  private implicit val context: ExecutionContext = ec

  override def execute(runnable: core.Runnable): Unit = Future { runnable.run() }

  override def delay(runnable: core.Runnable, millis: Long): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = execute(runnable)
    }, millis)
  }

}

object LedgerCoreExecutionContext {
  def apply(ec: ExecutionContext): LedgerCoreExecutionContext = new LedgerCoreExecutionContext(ec)
  def newThreadPool(): LedgerCoreExecutionContext = apply(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))
  def newSerialQueue(): LedgerCoreExecutionContext = apply(SerialExecutionContext.Implicits.global)
}
