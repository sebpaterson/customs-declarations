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

package acceptance

import java.util.UUID

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, OptionValues}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import util.CustomsDeclarationsExternalServicesConfig.CustomsNotificationAuthHeaderValue
import util.externalservices.CustomsNotificationService

import scala.concurrent.Future

class UploadedFileUpscanNotificationSpec extends AcceptanceTestSpec
  with Matchers
  with OptionValues
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with CustomsNotificationService {

  private val decId = UUID.randomUUID().toString
  private val eori = UUID.randomUUID().toString
  private val docType = "license"
  private val clientSubscriptionId = UUID.randomUUID().toString

  private val endpoint =
    s"/uploaded-file-upscan-notifications/decId/$decId/eori/$eori/documentationType/$docType/clientSubscriptionId/$clientSubscriptionId"


  override protected def beforeAll() {
    startMockServer()
  }

  override protected def beforeEach() {
    resetMockServer()
    registrationServiceIsRunning()
  }

  override protected def afterAll() {
    stopMockServer()
  }

  private val fileReference = UUID.randomUUID().toString
  val validRequest = FakeRequest("POST", endpoint).withJsonBody(Json.parse(
    s"""
       |{
       |    "reference" : "$fileReference",
       |    "fileStatus" : "READY",
       |    "url" : "https://some-url"
       |}
    """.stripMargin))

  val incorrectRequest = FakeRequest("POST", endpoint).withJsonBody(Json.parse(
    s"""
       |{
       |    "reference" : "$fileReference",
       |    "url" : "https://some-url"
       |}
    """.stripMargin))

  feature("Upscan notifications") {

    scenario("Notification is received successfully from upscan") {

      Given("A file has been uploaded by a CDS user")
      And("the uploaded file has been successfully scanned by upscan")


      When("upscan service notifies Declaration API using previously provided callback URL")

      val result: Future[Result] = route(app = app, validRequest).value

      Then("a response with a 204 status is returned")
      status(result) shouldBe 204

      And("the response body is empty")
      contentAsString(result) shouldBe 'empty

    }

    ignore("Correct request has been made to Customs Notification service") {

      Given("A file has been uploaded by a CDS user")
      And("the uploaded file has been successfully scanned by upscan")
      And("upscan service notifies Declaration API using previously provided callback URL")
      val result: Future[Result] = route(app = app, validRequest).value

      Then("a request is made to Custom Notification Service")
      val (requestHeaders, requestPayload) = aRequestWasMadeToNotificationService()

      And("The clientSubscriptionId is passed as X-CDS-Client-ID")
      requestHeaders.get("X-CDS-Client-ID") shouldBe Some(clientSubscriptionId)

      And("The reference is passed as X-Conversation-ID")
      requestHeaders.get("X-Conversation-ID") shouldBe Some(fileReference)

      And("The Authorization header contains the value which is configured in the configs")
      requestHeaders.get(AUTHORIZATION) shouldBe Some(CustomsNotificationAuthHeaderValue)

      And("The Content-Type header is application/xml; charset=UTF-8")
      requestHeaders.get(CONTENT_TYPE) shouldBe Some("application/xml; charset=UTF-8")

      And("The Accept header is application/xml")
      requestHeaders.get(ACCEPT) shouldBe Some("application/xml")


      And("The request XML payload contains details of the scan outcome")
      requestPayload shouldBe
        """
          |<?xml version="1.0" encoding="UTF-8"?>
          |<root>
          |   <FileStatus>SUCCESS</FileStatus>
          |   <details>"File successfully received"</details>
          |</root>
        """.stripMargin

    }

    scenario("Response status 400 when receive file upload status message payload is invalid") {

      Given("A file has been uploaded by a CDS user")
      And("the uploaded file has been successfully scanned by upscan")

      When("upscan service notifies Declaration API using previously provided callback URL with incorrect json payload")
      val result: Future[Result] = route(app = app, incorrectRequest).value

      Then("a response with a 400 status is returned")
      status(result) shouldBe 400

      Then("no request is made to Custom Notification Service")
      noRequestWasMadeToNotificationService()
    }

  }
}