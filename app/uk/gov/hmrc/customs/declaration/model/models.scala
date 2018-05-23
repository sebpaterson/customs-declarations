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

package uk.gov.hmrc.customs.declaration.model

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

import scala.xml.{Elem, Node, NodeSeq}

case class RequestedVersion(versionNumber: String, configPrefix: Option[String])

case class Eori(value: String) extends AnyVal {
  override def toString: String = value.toString
}

case class ClientId(value: String) extends AnyVal {
  override def toString: String = value.toString
}

case class ConversationId(uuid: UUID) extends AnyVal {
  override def toString: String = uuid.toString
}

case class CorrelationId(uuid: UUID) extends AnyVal {
  override def toString: String = uuid.toString
}

case class BadgeIdentifier(value: String) extends AnyVal {
  override def toString: String = value.toString
}

case class SubscriptionFieldsId(value: String) extends AnyVal{
  override def toString: String = value.toString
}

case class DeclarationId(value: String) extends AnyVal{
  override def toString: String = value.toString
}

case class DocumentationType(value: String) extends AnyVal{
  override def toString: String = value.toString
}

sealed trait ApiVersion {
  val value: String
  val configPrefix: String
  override def toString: String = value
}
object VersionOne extends ApiVersion{
  override val value: String = "1.0"
  override val configPrefix: String = ""
}
object VersionTwo extends ApiVersion{
  override val value: String = "2.0"
  override val configPrefix: String = "v2."
}

sealed trait AuthorisedAs
case class Csp(badgeIdentifier: BadgeIdentifier) extends AuthorisedAs
case class NonCsp(eori: Eori) extends AuthorisedAs

case class UpscanInitiatePayload(callbackUrl: String)

object UpscanInitiatePayload {
  implicit val format: OFormat[UpscanInitiatePayload] = Json.format[UpscanInitiatePayload]
}

case class FileUploadPayload(declarationID: String, documentationType: String)

case class UpscanInitiateResponsePayload(reference: String, uploadRequest: UpscanInitiateUploadRequest)

object UpscanInitiateUploadRequest {
  implicit val format: OFormat[UpscanInitiateUploadRequest] = Json.format[UpscanInitiateUploadRequest]
}

case class UpscanInitiateUploadRequest
(
  href: String,
  fields: Map[String, String]
)
{
  def addChild(n: NodeSeq, newChild: Node): NodeSeq = n match {
    case Elem(prefix, label, attribs, scope, child @ _*) =>
      Elem(prefix, label, attribs, scope, true, child ++ newChild : _*)
    case _ => sys.error("Can only add children to elements!")
  }

  def toXml: NodeSeq = {
    var payload: NodeSeq = <fileUpload>
      <href>
        {href}
      </href>
    </fileUpload>
    fields.foreach { f =>
      val tag = f._1
      val content = f._2
      payload = addChild(payload, <a/>.copy(label = tag, child = scala.xml.Text(content)))
    }
    payload
  }
}

object UpscanInitiateResponsePayload {
  implicit val format: OFormat[UpscanInitiateResponsePayload] = Json.format[UpscanInitiateResponsePayload]
}
