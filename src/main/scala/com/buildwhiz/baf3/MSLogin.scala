package com.buildwhiz.baf3

class MSLogin extends LoginBaseClass {

  override def validateIdToken(idTokenString: String, optEmail: Option[String]): (Boolean, String) = {
    optEmail match {
      case Some(email) => (true, email)
      case None => (false, "")
    }
  }

}
