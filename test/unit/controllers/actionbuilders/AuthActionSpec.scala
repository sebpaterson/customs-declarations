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

package unit.controllers.actionbuilders

import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorInternalServerError, UnauthorizedCode, errorBadRequest}
import uk.gov.hmrc.customs.declaration.connectors.GoogleAnalyticsConnector
import uk.gov.hmrc.customs.declaration.controllers.CustomHeaderNames
import uk.gov.hmrc.customs.declaration.controllers.actionbuilders.AuthAction
import uk.gov.hmrc.customs.declaration.logging.DeclarationsLogger
import uk.gov.hmrc.customs.declaration.model.GoogleAnalyticsValues
import uk.gov.hmrc.customs.declaration.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.declaration.model.actionbuilders.AnalyticsValuesAndConversationIdRequest
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._
import util.{AuthConnectorStubbing, RequestHeaders}

class AuthActionSpec extends UnitSpec with MockitoSugar {

  private val errorResponseUnauthorisedGeneral =
    ErrorResponse(Status.UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")
  private val errorResponseBadgeIdentifierHeaderMissing =
    errorBadRequest(s"${CustomHeaderNames.XBadgeIdentifierHeaderName} header is missing or invalid")
  private lazy val errorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")

  private lazy val validatedHeadersRequestWithValidBadgeId =
    AnalyticsValuesAndConversationIdRequest(conversationId, GoogleAnalyticsValues.Submit, testFakeRequestWithBadgeId()).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInValidBadgeIdTooLong =
    AnalyticsValuesAndConversationIdRequest(conversationId, GoogleAnalyticsValues.Submit, testFakeRequestWithBadgeId(badgeIdString = "INVALID_BADGE_IDENTIFIER_TO_LONG")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInValidBadgeIdLowerCase =
    AnalyticsValuesAndConversationIdRequest(conversationId, GoogleAnalyticsValues.Submit, testFakeRequestWithBadgeId(badgeIdString = "lowercase")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInValidBadgeIdTooShort =
    AnalyticsValuesAndConversationIdRequest(conversationId, GoogleAnalyticsValues.Submit, testFakeRequestWithBadgeId(badgeIdString = "SHORT")).toValidatedHeadersRequest(TestExtractedHeaders)
  private lazy val validatedHeadersRequestWithInValidBadgeIdInvalidChars =
    AnalyticsValuesAndConversationIdRequest(conversationId, GoogleAnalyticsValues.Submit, testFakeRequestWithBadgeId(badgeIdString = "(*&*(^&*&%")).toValidatedHeadersRequest(TestExtractedHeaders)


  trait SetUp extends AuthConnectorStubbing {
    val mockExportsLogger: DeclarationsLogger = mock[DeclarationsLogger]
    val mockGoogleAnalyticsConnector: GoogleAnalyticsConnector = mock[GoogleAnalyticsConnector]
    val authAction: AuthAction = new AuthAction(mockAuthConnector, mockExportsLogger, mockGoogleAnalyticsConnector)
  }

  "AuthAction Builder " can {
    "as CSP " should {
      "authorise as CSP when authorised by auth API and badge identifier exists" in new SetUp {
        authoriseCsp()

        private val actual = await(authAction.refine(validatedHeadersRequestWithValidBadgeId))
        actual shouldBe Right(validatedHeadersRequestWithValidBadgeId.toCspAuthorisedRequest(badgeIdentifier))
        verifyNonCspAuthorisationNotCalled
      }

      "Return 401 response when authorised by auth API but badge identifier does not exists" in new SetUp {
        authoriseCsp()

        private val actual = await(authAction.refine(TestValidatedHeadersRequestNoBadge))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
        verify(mockGoogleAnalyticsConnector).failure(errorResponseBadgeIdentifierHeaderMissing)(TestValidatedHeadersRequestNoBadge)
      }


      "Return 401 response when authorised by auth API but badge identifier exists but is too long" in new SetUp {
        authoriseCsp()

        private val actual = await(authAction.refine(validatedHeadersRequestWithInValidBadgeIdTooLong))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
        verify(mockGoogleAnalyticsConnector).failure(errorResponseBadgeIdentifierHeaderMissing)(validatedHeadersRequestWithInValidBadgeIdTooLong)
      }

      "Return 401 response when authorised by auth API but badge identifier exists but is too short" in new SetUp {
        authoriseCsp()

        private val actual = await(authAction.refine(validatedHeadersRequestWithInValidBadgeIdTooShort))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
        verify(mockGoogleAnalyticsConnector).failure(errorResponseBadgeIdentifierHeaderMissing)(validatedHeadersRequestWithInValidBadgeIdTooShort)
      }

      "Return 401 response when authorised by auth API but badge identifier exists but contains invalid chars" in new SetUp {
        authoriseCsp()

        private val actual = await(authAction.refine(validatedHeadersRequestWithInValidBadgeIdInvalidChars))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
        verify(mockGoogleAnalyticsConnector).failure(errorResponseBadgeIdentifierHeaderMissing)(validatedHeadersRequestWithInValidBadgeIdInvalidChars)
      }

      "Return 401 response when authorised by auth API but badge identifier exists but contains all lowercase chars" in new SetUp {
        authoriseCsp()

        private val actual = await(authAction.refine(validatedHeadersRequestWithInValidBadgeIdLowerCase))

        actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
        verify(mockGoogleAnalyticsConnector).failure(errorResponseBadgeIdentifierHeaderMissing)(validatedHeadersRequestWithInValidBadgeIdLowerCase)
      }

      "Return 500 response if errors occur in CSP auth API call" in new SetUp {
        authoriseCspError()

        private val actual = await(authAction.refine(TestValidatedHeadersRequestNoBadge))

        actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyNonCspAuthorisationNotCalled
      }
    }

    "As a Non CSP" should {
      "Authorise as Non CSP when authorised by auth API" in new SetUp {
        authoriseNonCsp(Some(declarantEori))

        private val actual = await(authAction.refine(TestValidatedHeadersRequestNoBadge))

        actual shouldBe Right(TestValidatedHeadersRequestNoBadge.toNonCspAuthorisedRequest(declarantEori))
        verifyCspAuthorisationCalled(1)
      }

      "Return 401 response when authorised by auth API but Eori not exists" in new SetUp {
        authoriseNonCsp(maybeEori = None)

        private val actual = await(authAction.refine(TestValidatedHeadersRequestNoBadge))

        actual shouldBe Left(errorResponseEoriNotFoundInCustomsEnrolment.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyCspAuthorisationCalled(1)
        verify(mockGoogleAnalyticsConnector).failure(errorResponseEoriNotFoundInCustomsEnrolment)(TestValidatedHeadersRequestNoBadge)
      }

      "Return 401 response when not authorised as NonCsp" in new SetUp {
        unauthoriseCsp()
        unauthoriseNonCspOnly()

        private val actual = await(authAction.refine(TestValidatedHeadersRequestNoBadge))

        actual shouldBe Left(errorResponseUnauthorisedGeneral.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyCspAuthorisationCalled(1)
        verify(mockGoogleAnalyticsConnector).failure(errorResponseUnauthorisedGeneral)(TestValidatedHeadersRequestNoBadge)
      }

      "Return 500 response if errors occur in CSP auth API call" in new SetUp {
        unauthoriseCsp()
        authoriseNonCspOnlyError()

        private val actual = await(authAction.refine(TestValidatedHeadersRequestNoBadge))

        actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
        verifyCspAuthorisationCalled(1)
      }
    }
  }

}
