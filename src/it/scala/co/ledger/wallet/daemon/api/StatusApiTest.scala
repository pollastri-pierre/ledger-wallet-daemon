package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.controllers.StatusController.VersionResponse
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.Status

class StatusApiTest extends APIFeatureTest {
  test("_version endpoint is returning buildinfo") {
    val versionResponse = parse[VersionResponse](server.httpGet("/_version", headers = defaultHeaders, andExpect = Status.Ok))
    assert(isValidVersion(versionResponse.libcoreVersion), versionResponse.libcoreVersion)
    assert(isValidVersion(versionResponse.version))
    assert(isValidVersion(versionResponse.scalaVersion))
    assert(isValidSHA(versionResponse.commitHash))
    assert(versionResponse.name == "wallet-daemon")
  }

  test("is valid version checker") {
    assert(isValidVersion("3.3.0"))
    assert(isValidVersion("3.3.0-86df3"))
    assert(!isValidVersion("0.0.0.0"))
    assert(!isValidVersion("0.0.0"))
    assert(!isValidVersion(""))
  }
  
  test("is valid sha checker") {
    assert(isValidSHA("dc0a1b846b1aba65989704f194fdfb5f3e8a8a22"))
    assert(!isValidSHA("unknown"))
  }

  def isValidVersion(version: String): Boolean = {
    // version has no blank spaces
    version.size == version.trim.size &&
      // Version is not only 0.0.0
      Some(version).map(_.replaceAll("[0.]", "")).exists(!_.isEmpty)
  }

  def isValidSHA(sha: String): Boolean = {
    sha.matches("[a-fA-F0-9]{40}")
  }
}
