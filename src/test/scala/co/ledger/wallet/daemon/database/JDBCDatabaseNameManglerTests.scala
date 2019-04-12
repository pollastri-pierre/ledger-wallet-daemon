package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.libledger_core.database.JDBCDatabaseNameMangler
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

/**
  * Tests for the database name mangler.
  *
  * User: Pierre Pollastri
  * Date: 24-01-2019
  * Time: 14:39
  *
  */
class JDBCDatabaseNameManglerTests extends AssertionsForJUnit {

  private val _mangler = new JDBCDatabaseNameMangler("tests")

  def transform(query: String, mangler: JDBCDatabaseNameMangler = _mangler): String = mangler.mangle(query)

  @Test
  def testSimpleSelect(): Unit = {
    assert(transform("SELECT * FROM tools WHERE id = 1") == "SELECT * FROM llc_tests_tools WHERE id = 1")
  }

  @Test
  def testJoinSelect(): Unit = {
    assert(transform("SELECT * FROM tools JOIN pieces ON pieces.tool_id = tools.id WHERE id = 1") ==
      "SELECT * FROM llc_tests_tools JOIN llc_tests_pieces ON llc_tests_pieces.tool_id = llc_tests_tools.id WHERE id = 1")
  }

  @Test
  def testInsert(): Unit = {
    assert(transform("INSERT INTO tools VALUES(1, 2 ,3)") == "INSERT INTO llc_tests_tools VALUES(1, 2 ,3)")
  }

  @Test
  def testCreateTable(): Unit = {
    assert(transform("CREATE TABLE tools (id INT)") == "CREATE TABLE llc_tests_tools (id INT)")
  }

  @Test
  def testCreateTableWithUnderscoredName(): Unit = {
    assert(transform("CREATE TABLE __tools__ (id INT)") == "CREATE TABLE llc_tests___tools__ (id INT)")
  }

  @Test
  def testCreateTableWithCascade(): Unit = {
    assert(transform("CREATE TABLE tools (id INT, name VARCHAR(255) REFERENCES category(name) ON DELETE CASCADE)")
      === "CREATE TABLE llc_tests_tools (id INT, name VARCHAR(255) REFERENCES llc_tests_category(name) ON DELETE CASCADE)")
  }

  @Test
  def testCreateTableWithCascadeAndMoreSpecs(): Unit = {
    assert(transform("CREATE TABLE tools (id INT, name VARCHAR(255) NOT NULL REFERENCES category(name) ON DELETE CASCADE)")
      === "CREATE TABLE llc_tests_tools (id INT, name VARCHAR(255) NOT NULL REFERENCES llc_tests_category(name) ON DELETE CASCADE)")
  }

  @Test
  def testAlterTable(): Unit = {
    assert(transform("ALTER TABLE bitcoin_currencies ADD COLUMN timestamp_delay BIGINT DEFAULT 0") ===
      "ALTER TABLE llc_tests_bitcoin_currencies ADD COLUMN timestamp_delay BIGINT DEFAULT 0")
  }

  @Test
  def testCreateTableWithUnderscoredNames(): Unit = {
    val mangler = new JDBCDatabaseNameMangler("test_db")
    assert(transform("CREATE TABLE ops_tools (id INT, name VARCHAR(255) REFERENCES ops_category(name) ON DELETE CASCADE)", mangler)
      === "CREATE TABLE llc_test_db_ops_tools (id INT, name VARCHAR(255) REFERENCES llc_test_db_ops_category(name) ON DELETE CASCADE)")
  }

  @Test
  def testCreateTableBitcoinCurrencies(): Unit = {
    val mangler = new JDBCDatabaseNameMangler("test_pool")
    assert(transform("CREATE TABLE bitcoin_currencies(name VARCHAR(255) PRIMARY KEY NOT NULL REFERENCES currencies(name) ON DELETE CASCADE ON UPDATE CASCADE,identifier VARCHAR(255) NOT NULL,p2pkh_version VARCHAR(255) NOT NULL,p2sh_version VARCHAR(255) NOT NULL,xpub_version VARCHAR(255) NOT NULL,dust_amount BIGINT NOT NULL,fee_policy VARCHAR(20) NOT NULL,message_prefix VARCHAR(255) NOT NULL,has_timestamped_transaction INTEGER NOT NULL)", mangler)
      === "CREATE TABLE llc_test_pool_bitcoin_currencies(name VARCHAR(255) PRIMARY KEY NOT NULL REFERENCES llc_test_pool_currencies(name) ON DELETE CASCADE ON UPDATE CASCADE,identifier VARCHAR(255) NOT NULL,p2pkh_version VARCHAR(255) NOT NULL,p2sh_version VARCHAR(255) NOT NULL,xpub_version VARCHAR(255) NOT NULL,dust_amount BIGINT NOT NULL,fee_policy VARCHAR(20) NOT NULL,message_prefix VARCHAR(255) NOT NULL,has_timestamped_transaction INTEGER NOT NULL)")
  }

}
