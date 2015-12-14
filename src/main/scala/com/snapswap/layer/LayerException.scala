package com.snapswap.layer

import scala.util.control.NoStackTrace

trait LayerException extends NoStackTrace {
  def message: String
  override def getMessage = message
}

case class UnexpectedLayerException(
                                     message: String, cause: Option[Throwable] = None
                                   ) extends LayerException {
  override def getCause = cause.orNull
}

case class LayerServiceUnavailable(message: String) extends LayerException
case class LayerInvalidRequest(message: String) extends LayerException
case class LayerRateLimitExceeded(message: String) extends LayerException
case class LayerRequestTimeout(message: String) extends LayerException
case class LayerConflict(message: String) extends LayerException

object LayerException {
  private val ctors: Map[String, String => LayerException] = Map(
    "service_unavailable" -> LayerServiceUnavailable.apply,
    "invalid_app_id" -> LayerInvalidRequest.apply,
    "invalid_request_id" -> LayerInvalidRequest.apply,
    "authentication_required" -> LayerInvalidRequest.apply,
    "rate_limit_exceeded" -> LayerRateLimitExceeded.apply,
    "request_timeout" -> LayerRequestTimeout.apply,
    "invalid_operation" -> LayerInvalidRequest.apply,
    "invalid_request" -> LayerInvalidRequest.apply,
    "internal_server_error" -> LayerServiceUnavailable.apply,
    "access_denied" -> LayerInvalidRequest.apply,
    "not_found" -> LayerInvalidRequest.apply,
    "missing_property" -> LayerInvalidRequest.apply,
    "invalid_property" -> LayerInvalidRequest.apply,
    "invalid_endpoint" -> LayerInvalidRequest.apply,
    "invalid_header" -> LayerInvalidRequest.apply,
    "conflict" -> LayerConflict.apply,
    "method_not_allowed" -> LayerInvalidRequest.apply
  )

  def apply(errorCode: Int, errorId: String, message: String): LayerException = {
    val m = s"[$errorCode:$errorId] $message"
    ctors.get(errorId).map(ctor => ctor(m)).getOrElse(UnexpectedLayerException(m))
  }
}
