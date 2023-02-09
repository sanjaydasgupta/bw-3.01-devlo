package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc

class MSLogin extends LoginBaseClass {

  override def validateIdToken(idTokenString: String, optEmail: Option[String]): (Boolean, String, DynDoc => Boolean) = {
    optEmail match {
      case Some(email) => (true, email, _ => true)
      case None => (false, "", _ => true)
    }
  }

}
