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

package unit.logging

import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames._
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.declaration.controllers.CustomHeaderNames
import uk.gov.hmrc.customs.declaration.logging.LoggingHelper
import uk.gov.hmrc.customs.declaration.model.actionbuilders.{ConversationIdRequest, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.declaration.model.{ClientId, ConversationId, VersionOne}
import uk.gov.hmrc.play.test.UnitSpec

class ExportsLoggingHelperSpec extends UnitSpec with MockitoSugar {

  private def expectedMessage(message: String) = "[conversationId=conversation-id]" +
    "[clientId=some-client-id]" +
    s"[requestedApiVersion=1.0] $message"
  private val requestMock = mock[Request[_]]
  private val conversationIdRequest =
    ConversationIdRequest(
      ConversationId("conversation-id"),
      FakeRequest().withHeaders(
        CONTENT_TYPE -> "A",
        ACCEPT -> "B",
        CustomHeaderNames.XConversationIdHeaderName -> "C",
        CustomHeaderNames.XClientIdHeaderName -> "D",
        "IGNORE" -> "IGNORE"
      )
    )
  private val validatedHeadersRequest = ValidatedHeadersRequest(ConversationId("conversation-id"), None, VersionOne, ClientId("some-client-id"), requestMock)

  "LoggingHelper" should {


    "testFormatInfo" in {
      LoggingHelper.formatInfo("Info message", validatedHeadersRequest) shouldBe expectedMessage("Info message")
    }

    "testFormatError" in {
      LoggingHelper.formatError("Error message", validatedHeadersRequest) shouldBe expectedMessage("Error message")
    }

    "testFormatWarn" in {
      LoggingHelper.formatWarn("Warn message", validatedHeadersRequest) shouldBe expectedMessage("Warn message")
    }

    "testFormatDebug" in {
      LoggingHelper.formatDebug("Debug message", validatedHeadersRequest) shouldBe expectedMessage("Debug message")
    }

    "testFormatDebugFull" in {
      LoggingHelper.formatDebugFull("Debug message.", conversationIdRequest) shouldBe s"[conversationId=conversation-id] Debug message. headers=Map(Accept -> B, X-Client-ID -> D, Content-Type -> A, X-Conversation-ID -> C)"
    }
  }
}