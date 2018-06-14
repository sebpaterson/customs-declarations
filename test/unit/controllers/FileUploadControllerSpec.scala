/*
 * Copyright 2018 HM Revenue & Customs
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

/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.controllers

import java.util.UUID

import akka.stream.Materializer
import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.mvc.{AnyContentAsXml, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{EmptyRetrieval, Retrievals}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.customs.declaration.connectors.GoogleAnalyticsConnector
import uk.gov.hmrc.customs.declaration.controllers.actionbuilders._
import uk.gov.hmrc.customs.declaration.controllers.{Common, FileUploadController}
import uk.gov.hmrc.customs.declaration.logging.DeclarationsLogger
import uk.gov.hmrc.customs.declaration.model.actionbuilders.{HasAnalyticsValues, HasConversationId, ValidatedUploadPayloadRequest}
import uk.gov.hmrc.customs.declaration.model.{ConversationId, UpscanInitiateResponsePayload, UpscanInitiateUploadRequest}
import uk.gov.hmrc.customs.declaration.services.{FileUploadBusinessService, FileUploadXmlValidationService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.RequestHeaders
import util.TestData._
import util.TestXMLData.ValidFileUploadXml

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FileUploadControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  private implicit val forConversions = TestConversationIdRequest
  private implicit val materializer: Materializer = app.materializer

  trait SetUp {
    val mockXmlValidationService: FileUploadXmlValidationService = mock[FileUploadXmlValidationService]
    val mockCdsLogger = mock[CdsLogger]
    val logger = new DeclarationsLogger(mockCdsLogger)
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    val stubConversationIdAction: ConversationIdAction = new ConversationIdAction(stubUniqueIdsService, logger)
    val stubAuthAction = new AuthAction(mockAuthConnector, logger, mockGoogleAnalyticsConnector)
    val stubValidateAndExtractHeadersAction: ValidateAndExtractHeadersAction = new ValidateAndExtractHeadersAction(new HeaderValidator(logger), logger, mockGoogleAnalyticsConnector)
    val common = new Common(stubConversationIdAction, stubAuthAction, stubValidateAndExtractHeadersAction, logger)
    val mockFileUploadBusinessService = mock[FileUploadBusinessService]
    val mockGoogleAnalyticsConnector = mock[GoogleAnalyticsConnector]

    val fileUploadPayloadValidationAction = new FileUploadPayloadValidationAction(mockXmlValidationService, logger, mockGoogleAnalyticsConnector)
    val fileUploadPayloadValidationComposedAction = new FileUploadPayloadValidationComposedAction(fileUploadPayloadValidationAction, logger)
    val fileUploadController = new FileUploadController(common, mockFileUploadBusinessService, fileUploadPayloadValidationComposedAction,
      new FileUploadAnalyticsValuesAction(logger), mockGoogleAnalyticsConnector)

    val upscanInitiateUploadRequest: UpscanInitiateUploadRequest = UpscanInitiateUploadRequest("http://some.url.com", Map("a" -> "b"))

    val upscanInitiateResponsePayload = UpscanInitiateResponsePayload(UUID.randomUUID().toString, upscanInitiateUploadRequest)

  }
  lazy val ValidRequest: FakeRequest[AnyContentAsXml] = FakeRequest("POST", "/file-upload")
    .withHeaders(
      RequestHeaders.X_CLIENT_ID_HEADER,
      RequestHeaders.ACCEPT_HMRC_XML_V1_HEADER,
      RequestHeaders.CONTENT_TYPE_HEADER
    ).withXmlBody(ValidFileUploadXml)

  "FileUploadController" should {

    "return HTTP response code 200 (OK) when a valid payload is sent" in new SetUp {
      val apiScope = "write:customs-declaration"
      val predicate: Predicate = Enrolment(apiScope) and AuthProviders(PrivilegedApplication)
      val customsEnrolmentName = "HMRC-CUS-ORG"
      val eoriIdentifier = "EORINumber"
      val customsEnrolment = Enrolment(customsEnrolmentName).withIdentifier(eoriIdentifier, "EORI123")
      val conversationId = new HasConversationId {
        override val conversationId: ConversationId = ConversationId(UUID.fromString(upscanInitiateResponsePayload.reference))
      }

      when(mockXmlValidationService.validate(ValidFileUploadXml)).thenReturn(Future.successful(()))
      when(mockAuthConnector.authorise(ameq(Enrolment(apiScope) and AuthProviders(PrivilegedApplication)), ameq(EmptyRetrieval))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new InsufficientEnrolments))
      when(mockAuthConnector.authorise(any, ameq(Retrievals.authorisedEnrolments))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Enrolments(Set(customsEnrolment)))
      when(mockFileUploadBusinessService.send(any[ValidatedUploadPayloadRequest[AnyContentAsXml]],any[HeaderCarrier])).thenReturn(Future.successful(Right(upscanInitiateResponsePayload)))

      val actual: Result = await(fileUploadController.post().apply(ValidRequest))

      status(actual) shouldBe Status.OK
      PassByNameVerifier(mockCdsLogger, "info")
        .withByNameParam(s"[conversationId=${upscanInitiateResponsePayload.reference}] Upload initiate request processed successfully.")
        .verify()
      verify(mockGoogleAnalyticsConnector).success(any[HasConversationId with HasAnalyticsValues])
    }
  }
}