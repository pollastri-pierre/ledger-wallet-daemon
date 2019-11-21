package co.ledger.wallet.daemon.async

import scala.concurrent.ExecutionContext

class SerialExecutionContextWrapper(ec: ExecutionContext) extends ExecutionContext with MDCPropagatingExecutionContext {
  override def execute(runnable: Runnable): Unit = {
    ec.execute(runnable)
  }
  override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
}
