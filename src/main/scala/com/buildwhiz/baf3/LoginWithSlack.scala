package com.buildwhiz.baf3

import java.util.Base64
import com.buildwhiz.infra.DynDoc
import org.bson.Document
class LoginWithSlack extends LoginBaseClass {

  override def validateIdToken(idTokenString: String, optEmail: Option[String]): (Boolean, String) = {
    val payload = idTokenString.split("[.]")(1)
    val json: String = new String(Base64.getUrlDecoder.decode(payload))
    val tokens: DynDoc = Document.parse(json)
    tokens.get[Boolean]("email_verified") match {
      case Some(true) => (true, tokens.email[String])
      case _ => (false, "")
    }
  }

}
