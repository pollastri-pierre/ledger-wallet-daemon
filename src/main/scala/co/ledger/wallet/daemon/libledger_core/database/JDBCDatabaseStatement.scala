package co.ledger.wallet.daemon.libledger_core.database

import java.sql.{PreparedStatement, Types}

import co.ledger.core.{DatabaseBlob, DatabaseColumn, DatabaseResultSet, DatabaseStatement}

import scala.util.Try

/**
  * JDBC implementation for Database engine
  *
  * User: Pierre Pollastri
  * Date: 16-01-2019
  * Time: 16:15
  *
  */
class JDBCDatabaseStatement(query: String, connection: JDBCDatabaseConnection) extends DatabaseStatement {

  private val _mangler = new JDBCDatabaseNameMangler(connection.databaseName)
  private val _queryString = normalizeQuery()
  private val _statement = prepareStatement()
  private var _resultSet: Option[JDBCResultSet] = None

  override def bindShort(index: Int, short: Short): Unit = _statement.setShort(index, short)

  override def bindInt(index: Int, int: Int): Unit = _statement.setInt(index, int)

  override def bindLong(index: Int, long: Long): Unit = _statement.setLong(index, long)

  override def bindFloat(index: Int, float: Float): Unit = _statement.setFloat(index, float)

  override def bindDouble(index: Int, double: Double): Unit = _statement.setDouble(index, double)

  override def bindString(index: Int, string: String): Unit = _statement.setString(index, string)

  override def bindBlob(index: Int, blob: DatabaseBlob): Unit = _statement.setBlob(index, blob.asInstanceOf[JDBCDatabaseBlob].toBlob())

  override def bindNull(index: Int): Unit = _statement.setNull(index, Types.NULL)

  override def describeColumn(index: Int): DatabaseColumn = obtainResultSet().describeColumn(index)

  override def getColumnCount: Int = obtainResultSet().getColumnCount

  override def execute(): DatabaseResultSet = obtainResultSet()

  override def reset(): Unit = {
    _statement.clearParameters()
    _resultSet.foreach(_.close())
    _resultSet = None
  }

  override def close(): Unit = Try {
    reset()
    if (!_statement.isClosed) {
      _statement.close()
    }
  }

  private def normalizeQuery(): String = {
    val queryWithParameters = query.replaceAll(":[a-zA-Z_0-9]+", "?")
    _mangler.mangle(queryWithParameters)
  }

  private def queryType: JDBCQueryType =
    if (_queryString.trim().toUpperCase.startsWith("SELECT")) JDBCSelectQueryType else JDBCUpdateQueryType

  private def prepareStatement(): PreparedStatement = {
    try {
      connection.connection.prepareStatement(_queryString)
    } catch {
      case all: Throwable =>
        all.printStackTrace()
        throw all
    }
  }

  private def obtainResultSet(): JDBCResultSet = {
    _resultSet.getOrElse({
      _resultSet = Some(new JDBCResultSet(_statement, queryType))
      _resultSet.get
    })
  }
}
