package co.ledger.wallet.daemon.controllers.responses

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.inject.Logging

object ResponseSerializer extends Logging {

  def serializeInternalError(request: Request, response: ResponseBuilder, caught: Throwable): Response = {
    val msg = Map(
      "response"->s"Internal server error occurred while processing the request. ${caught.getMessage}"
    )
    log((str: String) => error(str, caught), ErrorCode.Internal_Error, request, msg)
    response.internalServerError
      .body(ErrorResponseBody(ErrorCode.Internal_Error, msg))
  }

  def serializeBadRequest(request: Request, msg: Map[String, Any], response: ResponseBuilder): Response = {
    log((str) => info(str), ErrorCode.Bad_Request, request, msg)
    response.badRequest
      .body(ErrorResponseBody(ErrorCode.Bad_Request, msg))
  }

  def serializeNotFound(request: Request, msg: Map[String, Any], response: ResponseBuilder): Response = {
    log((str) => info(str), ErrorCode.Not_Found, request, msg)
    response.notFound
      .body(ErrorResponseBody(ErrorCode.Not_Found, msg))
  }

  private[this] def log(logLevel: String => Unit,
                        error: ErrorCode,
                        request: Request,
                        msg: Map[String, Any]): Unit = {
    logLevel(s"$error - [${request.method.name}] ${request.path} - Message : $msg")
  }

  def serializeOk(obj: Any, response: ResponseBuilder): Response = {
    info("Ok")
    response.ok(obj)
  }
}

