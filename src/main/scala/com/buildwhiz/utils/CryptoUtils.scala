package com.buildwhiz.utils

import java.security.MessageDigest

trait CryptoUtils {

  def md5(password: String): String = {
    val messageDigest = MessageDigest.getInstance("MD5")
    messageDigest.update(password.getBytes(), 0, password.length())
    val bytes = messageDigest.digest()
    val hexValues = bytes.map(b => "%02x".format(b))
    hexValues.mkString
  }

}
