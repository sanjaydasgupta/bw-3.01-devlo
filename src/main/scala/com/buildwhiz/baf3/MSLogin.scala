package com.buildwhiz.baf3

class MSLogin extends LoginBaseClass {

  override def validateIdToken(idTokenString: String, emailParameter: String): Boolean = {
    true
  }

}
