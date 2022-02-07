package com.buildwhiz.web

import java.io._
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.script.ScriptEngineManager
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.jdk.CollectionConverters._
import scala.collection.mutable

class LinkedCallback extends HttpServlet {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    writer.println(s"<!DOCTYPE html>")
    writer.println(s"<html><head><title>LinkedCallback</title></head>")
    val parameters = request.getParameterMap.asScala.map(e => (e._1, e._2.mkString))
    if (parameters.contains("code")) {
      writer.println( s"""<body><h2 align="center">OAuth Code Received</h2>""")
      val code = parameters("code")
      val state = parameters("state")
      val accessToken = getAccessToken(code)
      writer.println( s"""<body><h2 align="center">Access Token Received</h2>""")
      val profile = getProfile(accessToken("access_token").asInstanceOf[String])
      writer.println( s"""<body><h2 align="center">Profile Information Received</h2>""")
      writer.println(s"profile: $profile")
    } else if (parameters.contains("error")) {
      writer.println( s"""<body><h2 align="center">Login Failed</h2>""")
      val error = parameters("error")
      val errorDescription = parameters("error_description")
      writer.println(s"error: $error, description: $errorDescription")
    }
    writer.println("</body></html>")
  }

  private def getAccessToken(authCode: String): mutable.Map[String, AnyRef] = {
    val clientId = "75rniv37k7ekug"
    val callback = "https%3A%2F%2Fec2-52-10-9-236.us-west-2.compute.amazonaws.com%3A8443%2Fbuildwhiz-main%2Flinkedin-callback"
    val clientSecret = "KbIptSc15QwkR938"
    val url = new URL("https://www.linkedin.com/uas/oauth2/accessToken")
    val postData = s"grant_type=authorization_code&code=$authCode&redirect_uri=$callback&" +
      s"client_id=$clientId&client_secret=$clientSecret"
    val conn = url.openConnection().asInstanceOf[HttpsURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    conn.setDoOutput(true)
    conn.setDoInput(true)
    val outputStream = new DataOutputStream(conn.getOutputStream)
    outputStream.writeBytes(postData)
    outputStream.close()
    val inputStream = conn.getInputStream
    val reader = new BufferedReader(new InputStreamReader(inputStream))
    val json = reader.lines.toArray.mkString("\n")
    inputStream.close()
    json2map2(json)
  }

  private def getProfile(authorization: String): mutable.Map[String, AnyRef] = {
    val fields = ":(id,email-address,first-name,last-name,headline,location,industry,num-connections)"
    val url = new URL(s"https://api.linkedin.com/v1/people/~$fields?oauth2_access_token=$authorization&format=json")
    val conn = url.openConnection().asInstanceOf[HttpsURLConnection]
    val inputStream = conn.getInputStream
    val reader = new BufferedReader(new InputStreamReader(inputStream))
    val json = reader.lines.toArray.mkString("\n")
    inputStream.close()
    json2map2(json)
  }

  private def json2map2(json: String): mutable.Map[String, AnyRef] = {
    def convertInnerJavaMapsToScalaMaps(obj: mutable.Map[String, AnyRef]): mutable.Map[String, AnyRef] = {
      obj.map {
        case (key, javaMap: java.util.Map[String, AnyRef] @unchecked) =>
          val scalaMap: mutable.Map[String, AnyRef] = javaMap.asScala
          convertInnerJavaMapsToScalaMaps(scalaMap)
          (key, scalaMap)
        case other => other
      }
//      for ((k, v) <- obj) {
//        if (v.isInstanceOf[java.util.Map[String, AnyRef]]) {
//          val v2: mutable.Map[String, AnyRef] = v.asInstanceOf[java.util.Map[String, AnyRef]]
//          j2s(v2)
//          obj(k) = v2
//        }
//      }
    }
    val result = jsEngine.eval(s"var x = $json; x")
    val resultMap: mutable.Map[String, AnyRef] = result.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    convertInnerJavaMapsToScalaMaps(resultMap)
    resultMap
  }

  private val jsEngine = new ScriptEngineManager().getEngineByName("javascript")
}
