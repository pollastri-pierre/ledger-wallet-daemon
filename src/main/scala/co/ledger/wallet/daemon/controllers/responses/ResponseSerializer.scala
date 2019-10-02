package co.ledger.wallet.daemon.controllers.responses

import com.twitter.finagle.http.Response
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.inject.Logging

object ResponseSerializer extends Logging {

  def serializeInternalError(response: ResponseBuilder, caught: Throwable): Response = {
    error(ErrorCode.Internal_Error, caught)
    response.internalServerError
      .body(ErrorResponseBody(ErrorCode.Internal_Error, Map(
        "response"->s"Internal server error occurred while processing the request. ${caught.getMessage}"
      )))
  }

  def serializeBadRequest(msg: Map[String, Any], response: ResponseBuilder): Response = {
    info(ErrorCode.Bad_Request)
    response.badRequest
      .body(ErrorResponseBody(ErrorCode.Bad_Request, msg))
  }

  def serializeNotFound(msg: Map[String, Any], response: ResponseBuilder): Response = {
    info(ErrorCode.Not_Found)
    response.notFound
      .body(ErrorResponseBody(ErrorCode.Not_Found, msg))
  }

  def serializeOk(obj: Any, response: ResponseBuilder): Response = {
    info("Ok")
    response.ok(obj)
  }
}

