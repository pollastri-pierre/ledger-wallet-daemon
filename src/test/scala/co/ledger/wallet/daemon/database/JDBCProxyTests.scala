package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.Status

/**
  * Test cases for JDBCDatabaseEngine and related classes
  *
  * User: Pierre Pollastri
  * Date: 07-02-2019
  * Time: 17:06
  *
  */
class JDBCProxyTests extends APIFeatureTest {

  val PoolName = "jdbc_tests2"
  private val AccountBody =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'/0'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""

  test("Create a pool, add wallet, account, synchronize and get op") {
    createPool(PoolName)
    assertWalletCreation(PoolName, "wallet", "bitcoin", Status.Ok)
    assertCreateAccount(AccountBody, PoolName, "wallet", Status.Ok)
    assertSyncPool(Status.Ok)
    getFreshAddress(PoolName, "wallet", 0)
    val ops: Any = getOperation(PoolName, "wallet", 0)
    println(ops.toString)
  }

}
