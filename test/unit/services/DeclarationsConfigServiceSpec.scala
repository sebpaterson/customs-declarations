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

package unit.services

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.mockito.MockitoSugar
import play.api.{Configuration, Environment}
import uk.gov.hmrc.customs.api.common.config.{ConfigValidationNelAdaptor, ServicesConfig}
import uk.gov.hmrc.customs.declaration.logging.DeclarationsLogger
import uk.gov.hmrc.customs.declaration.services.DeclarationsConfigService
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier

class DeclarationsConfigServiceSpec extends UnitSpec with MockitoSugar {
  private val validAppConfig: Config = ConfigFactory.parseString(
    """
      |microservice.services.api-subscription-fields.host=some-host
      |microservice.services.api-subscription-fields.port=1111
      |microservice.services.api-subscription-fields.context=/some-context
      |microservice.services.customs-notification.host=some-host2
      |microservice.services.customs-notification.port=1112
      |microservice.services.customs-notification.bearer-token=some-token
      |microservice.services.customs-notification.context=/some-context2
      |microservice.services.google-analytics-sender.host=some-host3
      |microservice.services.google-analytics-sender.port=1113
      |microservice.services.google-analytics-sender.context=/some-context3
      |googleAnalytics.trackingId=ga-tr,
      |googleAnalytics.clientId=gl-cl-id,
      |googleAnalytics.eventValue=ga-ev-id
      |circuitBreaker.numberOfCallsToTriggerStateChange=5
      |circuitBreaker.unavailablePeriodDurationInMillis=1000
      |circuitBreaker.unstablePeriodDurationInMillis=1000
    """.stripMargin)

  private val emptyAppConfig: Config = ConfigFactory.parseString("")

  private val validServicesConfiguration = Configuration(validAppConfig)
  private val emptyServicesConfiguration = Configuration(emptyAppConfig)

  private val mockLogger = mock[DeclarationsLogger]

  private def customsConfigService(conf: Configuration) =
    new DeclarationsConfigService(new ConfigValidationNelAdaptor(testServicesConfig(conf), conf), mockLogger)

  "CustomsConfigService" should {
    "return config as object model when configuration is valid" in {
      val configService = customsConfigService(validServicesConfiguration)

      configService.declarationsConfig.apiSubscriptionFieldsBaseUrl shouldBe "http://some-host:1111/some-context"
      configService.declarationsConfig.customsNotificationBaseBaseUrl shouldBe "http://some-host2:1112/some-context2"
      configService.declarationsConfig.customsNotificationBearerToken shouldBe "some-token"
      configService.declarationsCircuitBreakerConfig.numberOfCallsToTriggerStateChange shouldBe 5
      configService.declarationsCircuitBreakerConfig.unavailablePeriodDurationInMillis shouldBe 1000
      configService.declarationsCircuitBreakerConfig.unstablePeriodDurationInMillis shouldBe 1000
    }

    "throw an exception when configuration is invalid, that contains AGGREGATED error messages" in {
      val expectedErrorMessage =
        """
          |Could not find config api-subscription-fields.host
          |Service configuration not found for key: api-subscription-fields.context
          |Could not find config customs-notification.host
          |Service configuration not found for key: customs-notification.context
          |Service configuration not found for key: customs-notification.bearer-token
          |Could not find config key 'circuitBreaker.numberOfCallsToTriggerStateChange'
          |Could not find config key 'circuitBreaker.unavailablePeriodDurationInMillis'
          |Could not find config key 'circuitBreaker.unstablePeriodDurationInMillis'
          |Could not find config google-analytics-sender.host
          |Service configuration not found for key: google-analytics-sender.context
          |Could not find config key 'googleAnalytics.trackingId'
          |Could not find config key 'googleAnalytics.clientId'
          |Could not find config key 'googleAnalytics.eventValue'""".stripMargin

      val caught = intercept[IllegalStateException](customsConfigService(emptyServicesConfiguration))
      caught.getMessage shouldBe expectedErrorMessage

      PassByNameVerifier(mockLogger, "errorWithoutRequestContext")
        .withByNameParam[String](expectedErrorMessage)
        .verify()
    }
  }

  private def testServicesConfig(configuration: Configuration) = new ServicesConfig(configuration, mock[Environment]) {
    override val mode = play.api.Mode.Test
  }

}