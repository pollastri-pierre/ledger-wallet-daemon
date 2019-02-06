package co.ledger.wallet.daemon.libledger_core.database

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.statement.create.table.{ColumnDefinition, CreateTable}
import net.sf.jsqlparser.util.TablesNamesFinder

import scala.annotation.tailrec
import scala.collection.JavaConverters._

/**
  * Describe your class here.
  *
  * User: Pierre Pollastri
  * Date: 24-01-2019
  * Time: 14:09
  *
  */
// scalastyle:off
class JDBCDatabaseNameMangler(prefix: String) {

  private val normalizedPrefix: String = "llc_" + prefix.replaceAll("-", "")

  def mangle(query: String): String = {
    val statement = CCJSqlParserUtil.parse(query)
    val tablesNamesFinder = new BetterTablesNamesFinder
    mangle(query, tablesNamesFinder.getTableList(statement).asScala.toList)
  }

  @tailrec
  private def mangle(query: String, tables: List[String]): String = tables match {
    case Nil => query
    case head :: tail =>
      println(s"Replace $head with ${normalizedPrefix}_$head ")
      mangle(query.replaceAll(s" $head", s" ${normalizedPrefix}_$head"), tail)
  }

  private class BetterTablesNamesFinder extends TablesNamesFinder {
    override def visit(create: CreateTable): Unit = {
      super.visit(create)
      create.getColumnDefinitions.asScala.foreach(visit)
    }


    override def visit(alter: Alter): Unit = visit(alter.getTable)

    def visit(column: ColumnDefinition): Unit = {
      @tailrec
      def go(specs: List[String]): Unit = specs match {
        case "references" :: table :: _ =>
          visit(References(table))
        case Nil => // End of loop
        case _ :: tail =>
          go(tail)
      }
      go(Option(column.getColumnSpecStrings).map(_.asScala.toList.map(_.toLowerCase())).getOrElse(Nil))
    }


    def visit(ref: References): Unit = {
      visit(new Table(ref.table))
    }


    case class References(table: String)
  }

}

// scalastyle:on