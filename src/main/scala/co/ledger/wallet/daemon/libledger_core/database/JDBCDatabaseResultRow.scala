package co.ledger.wallet.daemon.libledger_core.database

import java.sql.ResultSet

import co.ledger.core.{DatabaseBlob, DatabaseResultRow}

/**
  * Describe your class here.
  *
  * User: Pierre Pollastri
  * Date: 17-01-2019
  * Time: 11:24
  *
  */
class JDBCDatabaseResultRow(set: ResultSet, val rowNumber: Int) extends DatabaseResultRow {
  override def isNullAtPos(i: Int): Boolean = {
    println(s"CHECK NULL ${i +1}")
    set.getObject(i + 1)
    set.wasNull()
  }

  override def getColumnName(i: Int): String = row.getMetaData.getColumnName(i + 1)

  override def getShortByPos(i: Int): Short = row.getShort(i + 1)

  override def getIntByPos(i: Int): Int = row.getInt(i + 1)

  override def getFloatByPos(i: Int): Float = row.getFloat(i + 1)

  override def getDoubleByPos(i: Int): Double = row.getDouble(i + 1)

  override def getLongByPos(i: Int): Long = row.getLong(i + 1)

  override def getStringByPos(i: Int): String = {
    try {
      println(s"GETTING STRING ${i +1}")
      row.getString(i + 1)
    } catch {
      case all: Throwable =>
        all.printStackTrace()
        throw all
    }
  }

  override def getBlobByPos(i: Int): DatabaseBlob = new JDBCDatabaseBlob(row.getBlob(i))

  private val row: ResultSet = set
}
