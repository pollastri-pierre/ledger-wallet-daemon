package co.ledger.wallet.daemon.libledger_core.database

import java.sql.Connection

import co.ledger.core.{DatabaseBlob, DatabaseConnection, DatabaseStatement}

/**
  * Connection to the database used in the JDBC Database engine for lib-ledger-core
  *
  * User: Pierre Pollastri
  * Date: 16-01-2019
  * Time: 15:46
  *
  */
class JDBCDatabaseConnection(val connection: Connection, val databaseName: String) extends DatabaseConnection {

  override def prepareStatement(query: String, repeatable: Boolean): DatabaseStatement =
    new JDBCDatabaseStatement(query,this)

  override def begin(): Unit = connection.setAutoCommit(false)

  override def rollback(): Unit = connection.rollback()

  override def commit(): Unit = connection.commit()

  override def close(): Unit = {
    try {
      connection.close()
    } catch {
      case ex: Throwable => ex.printStackTrace()
    }
  }

  override def newBlob(): DatabaseBlob = new JDBCDatabaseBlob(connection.createBlob())
}
