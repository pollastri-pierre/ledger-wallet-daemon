package co.ledger.wallet.daemon.libledger_core.database

import co.ledger.core.DatabaseError

/**
  * Hold a JDBC error message in case of database error.
  *
  * User: Pierre Pollastri
  * Date: 17-01-2019
  * Time: 09:54
  *
  */
case class JDBCDatabaseError(message: String) extends DatabaseError {
  override def getMessage: String = message
}
