package co.ledger.wallet.daemon.controllers


import co.ledger.core.LedgerCore
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import co.ledger.wallet.daemon.BuildInfo

class StatusController extends Controller {
  import StatusController._

  /**
    * End point queries for the version of core library currently used.
    *
    */
  get("/_health") { request: Request =>
    info(s"GET _health $request")
    response.ok(Status(LedgerCore.getStringVersion))
  }

  get("/_version") { request: Request =>
    info(s"GET _version $request")
    response.ok(VersionResponse(BuildInfo.name, BuildInfo.commitHash, LedgerCore.getStringVersion, DaemonConfiguration.explorer))
  }
}

object StatusController {
  case class Status(engine_version: String)

  case class VersionResponse(name: String, commitHash: String, libcore: String, explorers: DaemonConfiguration.ExplorerConfig)
}
