package com.buildwhiz.utils

object CryptoUtilsTest extends App with CryptoUtils {
  println(md5(if (args.length > 0) args(0) else "abc"))
}
