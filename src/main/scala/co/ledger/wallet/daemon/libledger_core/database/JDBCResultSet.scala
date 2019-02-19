package co.ledger.wallet.daemon.libledger_core.database

import java.sql.{PreparedStatement, ResultSet, Types}

import co.ledger.core._

/**
  *
  *
  * User: Pierre Pollastri
  * Date: 17-01-2019
  * Time: 09:33
  *
  */

sealed trait RichDatabaseResultSet extends DatabaseResultSet {
  override def getRow: DatabaseResultRow = null
  override def getUpdateCount: Int = 0
  override def available(): Int = 0
  override def hasNext: Boolean = false
  override def next(): Unit = throw new RuntimeException("Trying to iterate rows from an empty database result set")
  override def close(): Unit = () // No op
  // scalastyle:off
  override def getError: DatabaseError = null
  def describeColumn(i: Int): DatabaseColumn = {
    if (i >= 0) null else null
  }
  // scalastyle:on
  def getColumnCount: Int = 0
}

class JDBCResultSet(statement: PreparedStatement, queryType: JDBCQueryType) extends RichDatabaseResultSet {
  override def getRow: DatabaseResultRow = _set.getRow
  override def getUpdateCount: Int = _set.getUpdateCount
  override def available(): Int = _set.available()
  override def hasNext: Boolean = _set.hasNext
  override def next(): Unit = _set.next()
  override def close(): Unit = _set.close()
  override def getError: DatabaseError = _set.getError
  override def describeColumn(i: Int): DatabaseColumn = _set.describeColumn(i)
  override def getColumnCount: Int = _set.getColumnCount

  private val _set: UnifiedResultSet = {
    try {
      statement.execute()
      queryType match {
        case JDBCSelectQueryType =>
          val rs = statement.getResultSet
          val isEmpty = !rs.isBeforeFirst
          QueryResultSet(rs, isEmpty, new JDBCDatabaseResultRow(rs, 1))
        case _ =>
          UpdateResultSet(statement.getUpdateCount)
      }
    } catch {
      case all: Throwable =>
        all.printStackTrace()
        throw all
    }
  }

  sealed trait UnifiedResultSet extends RichDatabaseResultSet

  private case class QueryResultSet(var set: ResultSet, empty: Boolean, var row: JDBCDatabaseResultRow) extends UnifiedResultSet {
    override def getRow: DatabaseResultRow = if (!set.isAfterLast && !empty) row else null

    override def available(): Int = {
      if (!set.isAfterLast && !set.isLast && !empty) 1 else 0
    }

    override def hasNext: Boolean = available() > 0

    override def next(): Unit = {
      if (!hasNext) throw new RuntimeException("Attempt to call next on a final cursor")
      set.next()
      row = new JDBCDatabaseResultRow(set, set.getRow)
    }

    override def close(): Unit = if (!set.isClosed) set.close()

    override def describeColumn(i: Int): DatabaseColumn = {
      val meta = set.getMetaData
      JDBCDatabaseColumn.typeMatcher(meta.getColumnType(i)) match {
        case Some(t) => JDBCDatabaseColumn(t, meta.getColumnName(i))
        case None => throw new RuntimeException(s"Column $i has an unhandled type ${meta.getColumnType(i)}")
      }
    }
    override def getColumnCount: Int = set.getMetaData.getColumnCount

  }

  private case class EmptyResultSet() extends UnifiedResultSet {
    override def getRow: DatabaseResultRow = new DatabaseResultRow {
      override def isNullAtPos(i: Int): Boolean = true

      override def getColumnName(i: Int): String = ""

      override def getShortByPos(i: Int): Short = 0

      override def getIntByPos(i: Int): Int = 0

      override def getFloatByPos(i: Int): Float = 0.0f

      override def getDoubleByPos(i: Int): Double = 0.0

      override def getLongByPos(i: Int): Long = 0L

      override def getStringByPos(i: Int): String = ""

      override def getBlobByPos(i: Int): DatabaseBlob = null
    }
  }

  private case class UpdateResultSet(updateCount: Int) extends UnifiedResultSet {
    override def getUpdateCount: Int = updateCount
  }

  private case class ErrorResultSet(error: Exception) extends UnifiedResultSet {
    override def getError: DatabaseError = JDBCDatabaseError(error.getMessage)
  }
}

case class JDBCDatabaseColumn(valueType: DatabaseValueType, name: String) extends DatabaseColumn {
  override def getType: DatabaseValueType = valueType
  override def getName: String = name
}

object JDBCDatabaseColumn {

  val typeMatcher: Map[Int, Option[DatabaseValueType]] = Map(
    Types.ARRAY -> None,
    Types.BIGINT -> Some(DatabaseValueType.LONG_LONG),
    Types.BINARY -> None,
    Types.BIT -> None,
    Types.BLOB -> Some(DatabaseValueType.BLOB),
    Types.BOOLEAN -> Some(DatabaseValueType.INTEGER),
    Types.CHAR -> None,
    Types.CLOB -> Some(DatabaseValueType.BLOB),
    Types.DATALINK -> None,
    Types.DATE -> Some(DatabaseValueType.DATE),
    Types.DECIMAL -> Some(DatabaseValueType.DOUBLE),
    Types.DISTINCT -> None,
    Types.DOUBLE -> Some(DatabaseValueType.DOUBLE),
    Types.FLOAT -> Some(DatabaseValueType.DOUBLE),
    Types.INTEGER -> Some(DatabaseValueType.INTEGER),
    Types.JAVA_OBJECT -> None,
    Types.LONGNVARCHAR -> Some(DatabaseValueType.STRING),
    Types.LONGVARBINARY -> None,
    Types.LONGVARCHAR -> Some(DatabaseValueType.STRING),
    Types.NCHAR -> None,
    Types.NCLOB -> None,
    Types.NULL -> None,
    Types.NUMERIC -> Some(DatabaseValueType.DOUBLE),
    Types.NVARCHAR -> Some(DatabaseValueType.STRING),
    Types.OTHER -> None,
    Types.REAL -> Some(DatabaseValueType.DOUBLE),
    Types.REF -> None,
    Types.REF_CURSOR -> None,
    Types.ROWID -> None,
    Types.SMALLINT -> Some(DatabaseValueType.INTEGER),
    Types.SQLXML -> None,
    Types.STRUCT -> None,
    Types.TIME -> None,
    Types.TIME_WITH_TIMEZONE -> None,
    Types.TIMESTAMP -> None,
    Types.TIMESTAMP_WITH_TIMEZONE -> None,
    Types.TINYINT -> None,
    Types.VARBINARY -> None,
    Types.VARCHAR -> Some(DatabaseValueType.STRING)
  )

}

sealed trait JDBCQueryType
case object JDBCSelectQueryType extends JDBCQueryType
case object JDBCUpdateQueryType extends JDBCQueryType