package co.ledger.wallet.daemon.filters

import javax.inject.Inject
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.services.AuthenticationService
import co.ledger.wallet.daemon.services.AuthenticationService.AuthContextContext._
import co.ledger.wallet.daemon.services.AuthenticationService.{UserNotFoundException, AuthentifiedUserContext}
import co.ledger.wallet.daemon.utils.HexUtils
import co.ledger.wallet.daemon.utils.Utils._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future

class AuthenticationFilter @Inject()(authService: AuthenticationService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    authService.authorize(request) flatMap {(_) =>
      service(request)
    }
  }
}

class NoAuthenticationFilter @Inject()(daemonCache: DaemonCache) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
   val pubKey = request.authContext.pubKey
    daemonCache.getUser(HexUtils.valueOf(pubKey)) map {
      case None =>
        throw UserNotFoundException()
      case Some(user) =>
         AuthentifiedUserContext.setUser(request, user)
    } asTwitter() flatMap {(_) =>
      service(request)
    }
  }
}
