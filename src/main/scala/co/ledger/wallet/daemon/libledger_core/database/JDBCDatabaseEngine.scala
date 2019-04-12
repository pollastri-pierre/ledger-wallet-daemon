package co.ledger.wallet.daemon.libledger_core.database

import co.ledger.core.{DatabaseConnection, DatabaseConnectionPool, DatabaseEngine}
import slick.jdbc.{JdbcDataSource}

/**
  * Core library implementation of a database engine using JDBC as its backend.
  *
  * User: Pierre Pollastri
  * Date: 16-01-2019
  * Time: 10:58
  *
  */
class JDBCDatabaseEngine(profile: JdbcDataSource) extends DatabaseEngine {

  override def connect(databaseName: String): DatabaseConnectionPool = new DatabaseConnectionPool {
    override def getConnection: DatabaseConnection = new JDBCDatabaseConnection(profile.createConnection(), databaseName)
  }

  override def getPoolSize: Int = 1 //profile.maxConnections.getOrElse(Runtime.getRuntime.availableProcessors())

}
