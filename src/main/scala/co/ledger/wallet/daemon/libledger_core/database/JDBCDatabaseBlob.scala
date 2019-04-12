package co.ledger.wallet.daemon.libledger_core.database

import java.sql.Blob

import co.ledger.core.DatabaseBlob

/**
  * Wrapper around JDBC BLOB implementation for core library database engine.
  *
  * User: Pierre Pollastri
  * Date: 16-01-2019
  * Time: 16:14
  *
  */
class JDBCDatabaseBlob(blob: Blob) extends DatabaseBlob {
  override def read(offset: Long, length: Long): Array[Byte] = blob.getBytes(offset, length.toInt)

  override def write(offset: Long, bytes: Array[Byte]): Long = blob.setBytes(offset, bytes)

  override def append(bytes: Array[Byte]): Long = blob.setBytes(blob.length(), bytes)

  override def trim(l: Long): Long = {
    val s = blob.length() - l
    blob.truncate(l.toInt)
    s
  }

  override def size(): Long = blob.length()

  def toBlob(): Blob = blob
}
