package com.buildwhiz.baf3

import java.util.Collections
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory

class GLogin extends LoginBaseClass {

  override def validateIdToken(idTokenString: String, emailParameter: String): Boolean = {
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = JacksonFactory.getDefaultInstance
    val clientId = "318594985671-gfojh2kiendld330k65eajmjdifudpct.apps.googleusercontent.com"
    val verifier = new GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory).
        setAudience(Collections.singletonList(clientId)).build()
    val idToken = verifier.verify(idTokenString)
    if (idToken != null) {
      val payload = idToken.getPayload
      val email = payload.getEmail
      val emailVerified: Boolean = payload.getEmailVerified
      emailVerified && (email == emailParameter)
    } else {
      false
    }
  }

}
