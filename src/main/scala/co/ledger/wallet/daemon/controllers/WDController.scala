package co.ledger.wallet.daemon.controllers

import com.twitter.finagle.http.RouteIndex
import com.twitter.finatra.http.Controller
import com.twitter.inject.Logging

abstract class WDController extends Controller with Logging {
  def deprecated[RequestType: Manifest, ResponseType: Manifest]
  ( fn: (String, String, Boolean, Option[RouteIndex]) => (RequestType => ResponseType) => Unit,
    route: String,
    name: String = "",
    admin: Boolean = false,
    index: Option[RouteIndex] = None,
    callback: RequestType => ResponseType): Unit = {
    warn(s"Route $route ($name) is deprecated !!! Please refer to the documentation")
    fn(route, name, admin, index)(callback)
  }
}
