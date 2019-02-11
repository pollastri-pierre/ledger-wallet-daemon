package co.ledger.wallet.daemon.utils

import java.io.File
import java.util.Date

import co.ledger.wallet.daemon.ServerImpl
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.services.ECDSAService
import com.lambdaworks.codec.Base64
import com.twitter.finagle.http.{Response, Status}
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import org.bitcoinj.core.Sha256Hash

trait APIFeatureTest extends FeatureTest {
  override val server = new EmbeddedHttpServer(new ServerImpl)
  def defaultHeaders = lwdBasicAuthorisationHeader("whitelisted")
  def parse[A](response: Response)(implicit manifest: Manifest[A]): A = server.mapper.parse[A](response)

  def assertWalletCreation(poolName: String, walletName: String, currencyName: String, expected: Status): Response = {
    server.httpPost(path = s"/pools/$poolName/wallets",
      postBody = s"""{\"currency_name\":\"$currencyName\",\"wallet_name\":\"$walletName\"}""",
      headers = defaultHeaders,
      andExpect = expected)
  }

  def getPools(): Response = {
    server.httpGet("/pools", headers = defaultHeaders, andExpect = Status.Ok)
  }

  def getPool(poolName: String): Response = {
    getPool(poolName, Status.Ok)
  }

  def getPool(poolName: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName", headers = defaultHeaders, andExpect = expected)
  }

  def createPool(poolName: String, expected: Status = Status.Ok): Response = {
    server.httpPost("/pools", s"""{"pool_name":"$poolName"}""", headers = defaultHeaders, andExpect = expected)
  }

  def deletePool(poolName: String): Response = {
    server.httpDelete(s"/pools/$poolName", "", headers = defaultHeaders)
  }

  def assertSyncPool(expected: Status): Response = {
    server.httpPost("/pools/operations/synchronize", "", headers = defaultHeaders, andExpect = expected)
  }

  protected def assertCreateAccount(accountCreationBody: String, poolName: String, walletName: String, expected: Status): Response = {
    server.httpPost(s"/pools/$poolName/wallets/$walletName/accounts", accountCreationBody, headers = defaultHeaders, andExpect = expected)
  }

  def getOperation(poolName: String, walletName: String, accountIndex: Int): Any = {
    server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/operations", headers = defaultHeaders).contentString
  }

  def getFreshAddress(poolName: String, walletName: String, accountIndex: Int): Unit = {
    server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/addresses/fresh", headers = defaultHeaders).contentString
  }

  private def lwdBasicAuthorisationHeader(seedName: String, time: Date = new Date()) = {
    val ecdsa = server.injector.instance(classOf[ECDSAService])
    val privKey = Sha256Hash.hash(FixturesUtils.seed(seedName).getBytes)
    val pubKey = ecdsa.computePublicKey(privKey)
    val timestamp = time.getTime / 1000
    val message = Sha256Hash.hash(s"LWD: $timestamp\n".getBytes)
    val signed = ecdsa.sign(message, privKey)
    Map(
      "authorization" -> s"LWD ${Base64.encode(s"${HexUtils.valueOf(pubKey)}:$timestamp:${HexUtils.valueOf(signed)}".getBytes).mkString}"
    )
  }

  protected override def beforeAll(): Unit = {
    cleanup()
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    cleanup()
  }

  private def cleanup(): Unit = {
    val directory = new ScalaPathResolver("").installDirectory
    if (directory.exists()) {
      directory.listFiles().filter(_.isDirectory).foreach(deleteDirectory)
    }
  }

  private def deleteDirectory(directory: File): Unit = {
    for (f <- directory.listFiles()) {
      if (f.isDirectory) {
        deleteDirectory(f)
      } else {
        f.delete()
      }
    }
    directory.delete()
  }

}
