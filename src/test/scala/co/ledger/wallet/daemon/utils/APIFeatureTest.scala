package co.ledger.wallet.daemon.utils

import java.io.File
import java.util.{Date, UUID}

import co.ledger.core
import co.ledger.wallet.daemon.{ServerImpl, models}
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.services.ECDSAService
import com.fasterxml.jackson.annotation.JsonProperty
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

  def getOperation(poolName: String, walletName: String, accountIndex: Int): APIFeatureTest.PackedOperationsView = {
   parse[APIFeatureTest.PackedOperationsView](server.httpGet(
     s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/operations",
     headers = defaultHeaders
   ))
  }

  def getFreshAddress(poolName: String, walletName: String, accountIndex: Int): List[models.FreshAddressView] = {
    parse[List[models.FreshAddressView]](
      server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/addresses/fresh",
        headers = defaultHeaders
      ))
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

object APIFeatureTest {
  case class OperationView(
                            @JsonProperty("uid") uid: String,
                            @JsonProperty("currency_name") currencyName: String,
                            @JsonProperty("currency_family") currencyFamily: core.WalletType,
                            @JsonProperty("trust") trust: Option[TrustIndicatorView],
                            @JsonProperty("confirmations") confirmations: Long,
                            @JsonProperty("time") time: Date,
                            @JsonProperty("block_height") blockHeight: Option[Long],
                            @JsonProperty("type") opType: core.OperationType,
                            @JsonProperty("amount") amount: BigInt,
                            @JsonProperty("fees") fees: BigInt,
                            @JsonProperty("wallet_name") walletName: String,
                            @JsonProperty("account_index") accountIndex: Int,
                            @JsonProperty("senders") senders: Seq[String],
                            @JsonProperty("recipients") recipients: Seq[String],
                          )

  case class TrustIndicatorView(
                                 @JsonProperty("weight") weight: Int,
                                 @JsonProperty("level") level: core.TrustLevel,
                                 @JsonProperty("conflicted_operations") conflictedOps: Seq[String],
                                 @JsonProperty("origin") origin: String
                               )

  case class PackedOperationsView(
                                   @JsonProperty("previous") previous: Option[UUID],
                                   @JsonProperty("next") next: Option[UUID],
                                   @JsonProperty("operations") operations: Seq[OperationView]
                                 )
}