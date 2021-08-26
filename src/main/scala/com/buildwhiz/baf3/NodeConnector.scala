package com.buildwhiz.baf3

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.apache.http.HttpResponse
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpRequestBase}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.entity.mime.{HttpMultipartMode, MultipartEntityBuilder}
import org.apache.http.impl.client.HttpClients
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._
import scala.io.Source

object NodeConnector extends HttpServlet with HttpUtils {

  private def nodeUri(request: HttpServletRequest): String = {
    if (request.getParameter("uid") != null)
      throw new IllegalArgumentException("Bad parameter name 'uid' found")
    val user: DynDoc = getPersona(request)
    val loggedInUser: DynDoc = getUser(request)
    val loggedInUserName = PersonApi.fullName(loggedInUser).replace(" ", "%20")
    val userParam = s"uid=${user._id[ObjectId]}&u$$nm=$loggedInUserName"
    val urlParts = request.getRequestURL.toString.split("/")
    val pkgIdx = urlParts.zipWithIndex.find(_._1.matches("api|baf[23]?|dot|etc|graphql|slack|tools|web")).head._2
    val serviceName = urlParts(pkgIdx + 1)
    val serviceNameWithParameters = request.getParameterMap.asScala.filterNot(_._1 == "JSESSIONID") match {
      case params if params.nonEmpty =>
        serviceName + params.map(kv => s"${kv._1}=${kv._2.head}").mkString("?", "&", "&") + userParam
      case _ => serviceName + "?" + userParam
    }
    s"http://localhost:3000/$serviceNameWithParameters"
  }

  private def executeNodeRequest(request: HttpServletRequest, response: HttpServletResponse,
      nodeRequest: HttpRequestBase): Unit = {
    def exitStatus(httpResponse: HttpResponse): String = {
      if (httpResponse.getStatusLine.getStatusCode == 200) {
        "OK"
      } else {
        s"WARN ${httpResponse.getStatusLine.getStatusCode} (${httpResponse.getStatusLine.getReasonPhrase})"
      }
    }
    val nodeResponse = HttpClients.createDefault().execute(nodeRequest)
    nodeResponse.getAllHeaders.foreach(hdr => response.addHeader(hdr.getName, hdr.getValue))
    response.setStatus(nodeResponse.getStatusLine.getStatusCode)
    val nodeEntity = nodeResponse.getEntity
    val message = if (nodeEntity != null) {
      val nodeEntityContentType = nodeEntity.getContentType.getValue
      BWLogger.log(getClass.getName, request.getMethod, s"executeNodeRequest():contentType=$nodeEntityContentType", request)
      if (nodeEntityContentType.startsWith("application/json")) {
        val nodeEntityString = Source.fromInputStream(nodeEntity.getContent).getLines.mkString("\n")
        val nodeEntityDocument = Document.parse(nodeEntityString)
        val user: DynDoc = getPersona(request)
        val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
        nodeEntityDocument.append("menu_items", displayedMenuItems(isAdmin))
        val updatedNodeEntityString = nodeEntityDocument.toJson
        val containsMenuItems = updatedNodeEntityString.contains("menu_items")
        //BWLogger.log(getClass.getName, request.getMethod, s"executeNodeRequest():menu_items=$containsMenuItems", request)
        val updatedNodeEntity = new StringEntity(updatedNodeEntityString, ContentType.create("application/json", "utf-8"))
        response.setHeader("Content-Length", updatedNodeEntity.getContentLength.toString)
        updatedNodeEntity.writeTo(response.getOutputStream)
        s"${exitStatus(nodeResponse)} - Length:${updatedNodeEntity.getContentLength};Type:${updatedNodeEntity.getContentType}"
      } else {
        nodeEntity.writeTo(response.getOutputStream)
        s"${exitStatus(nodeResponse)} - Length:${nodeEntity.getContentLength};Type:${nodeEntity.getContentType}"
      }
    } else {
      exitStatus(nodeResponse)
    }
    nodeRequest.releaseConnection()
    BWLogger.log(getClass.getName, request.getMethod, s"EXIT-$message", request)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val httpGet = new HttpGet(nodeUri(request))
      request.getHeaderNames.asScala.foreach(hdrName => httpGet.setHeader(hdrName, request.getHeader(hdrName)))
      executeNodeRequest(request, response, httpGet)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val httpPost = new HttpPost(nodeUri(request))
      request.getHeaderNames.asScala.filterNot(_ == "Content-Length").
          foreach(hdrName => httpPost.setHeader(hdrName, request.getHeader(hdrName)))
      val contentType = request.getContentType
      contentType.split(";").head.trim match {
        case "application/json" =>
          val jsonText = getStreamData(request)
          val stringEntity = new StringEntity(jsonText)
          httpPost.setEntity(stringEntity)
          BWLogger.log(getClass.getName, request.getMethod, s"content($contentType) text: [$jsonText]", request)
        case "multipart/form-data" =>
          val entityBuilder = MultipartEntityBuilder.create()
          entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
          var totalLength: Long = 0
          for (part <- request.getParts.iterator.asScala) {
            totalLength += part.getSize
            val partContentType = ContentType.create(part.getContentType)
            entityBuilder.addBinaryBody(part.getName, part.getInputStream, partContentType, part.getSubmittedFileName)
          }
          val multipartEntity = entityBuilder.build()
          httpPost.setEntity(multipartEntity)
          httpPost.setHeader("Content-Type", multipartEntity.getContentType.getValue)
          BWLogger.log(getClass.getName, request.getMethod, s"content($contentType) length: $totalLength", request)
        case _ =>
          throw new IllegalArgumentException(s"unknown contentType: '$contentType'")
      }
      httpPost.removeHeaders("Content-Length")
      executeNodeRequest(request, response, httpPost)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}