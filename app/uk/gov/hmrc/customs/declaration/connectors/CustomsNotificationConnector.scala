/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.customs.declaration.connectors

import com.google.inject.{Inject, Singleton}
import play.mvc.Http.HeaderNames._
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.customs.declaration.services.{CustomsNotification, DeclarationsConfigService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CustomsNotificationConnector @Inject()(http: HttpClient,
                                             logger: CdsLogger,
                                             config: DeclarationsConfigService) {

  private implicit val hc = HeaderCarrier()
  private val XMLHeader = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

  def send(notification: CustomsNotification): Future[Unit] = {

    val headers: Map[String, String] = Map(
      "X-CDS-Client-ID" -> notification.clientSubscriptionId,
      "X-Conversation-ID" -> notification.conversationId.toString,
      CONTENT_TYPE -> s"${MimeTypes.XML}; charset=UTF-8",
      ACCEPT -> MimeTypes.XML,
      AUTHORIZATION -> s"Basic ${config.declarationsConfig.customsNotificationBearerToken}")


    http.POSTString(
      config.declarationsConfig.customsNotificationBaseBaseUrl,
      XMLHeader + notification.payload.toString(),
      headers.toSeq
    ) map { _ =>
      logger.info(s"[conversationId=${notification.conversationId}][clientSubscriptionId=${notification.clientSubscriptionId}]: Upscan notification processed successfully ")
      ()
    } recover {
      case e: Throwable =>
        logger.error(s"[conversationId=${notification.conversationId}][clientSubscriptionId=${notification.clientSubscriptionId}] FileUploadScanNotificationHandler -> Request to Customs Notification failed.", e)
    }
  }

}
