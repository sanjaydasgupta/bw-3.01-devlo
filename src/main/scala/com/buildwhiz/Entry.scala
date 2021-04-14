package com.buildwhiz

import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, HttpSession}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._

import scala.collection.mutable
import scala.collection.JavaConverters._

import scala.util.{Failure, Success, Try}
import scala.language.reflectiveCalls
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients

@MultipartConfig()
class Entry extends HttpServlet with HttpUtils {

  private def permitted(request: HttpServletRequest): Boolean = {
    val session: HttpSession = getSessionAlternatives(request)
    val uriParts = request.getRequestURI.split("/")
    val loggingIn = (uriParts.last, uriParts.init.last) match {
      case ("LoginPost", "etc") => true
      case ("Environment", "etc") => true
      case ("Login", "baf2") => true
      case ("Logout", "baf2") => true
      case ("GLogin", "baf3") => true
      case ("Logout", "baf3") => true
      case ("SlackSlashCommand", "slack") => true
      case ("SlackEventCallback", "slack") => true
      case ("SlackInteractiveCallback", "slack") => true
      case _ => false
    }
    session.getAttribute("bw-user") != null || loggingIn
  }

  private def log(event: String, request: HttpServletRequest): Unit = {
    val urlParts = request.getRequestURL.toString.split("/")
    urlParts.zipWithIndex.find(_._1.matches("api|baf[23]?|dot|etc|graphql|slack|tools|web")) match {
      case Some((_, pkgIdx)) =>
        val apiPath = s"${urlParts(pkgIdx)}/${urlParts(pkgIdx + 1)}"
        BWLogger.log(apiPath, request.getMethod, event, request)
      case None =>
        BWLogger.log("Unknown", request.getMethod, event, request)
    }
  }

  private def invokeNodeJs(serviceName: String, request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY (invokeNodeJs)", request)
    if (request.getParameter("uid") != null)
      throw new IllegalArgumentException("Bad parameter name 'uid' found")
    val user: DynDoc = getUser(request)
    val userParam = s"uid=${user._id[ObjectId]}"
    val serviceNameWithParameters = request.getParameterMap.asScala match {
      case params if params.nonEmpty =>
          serviceName + params.map(kv => s"${kv._1}=${kv._2.head}").mkString("?", "&", "&") + userParam
      case _ => serviceName + "?" + userParam
    }
    val nodeUri = s"http://localhost:3000/$serviceNameWithParameters"
    val nodeRequest = request.getMethod match {
      case "GET" => val httpGet = new HttpGet(nodeUri)
        request.getHeaderNames.asScala.foreach(hdrName => httpGet.setHeader(hdrName, request.getHeader(hdrName)))
        val headers = httpGet.getAllHeaders.map(header => s"${header.getName}=${header.getValue}").mkString(";")
        BWLogger.log(getClass.getName, request.getMethod, s"nodeUri: $nodeUri, headers: $headers", request)
        httpGet
      case "POST" => val httpPost = new HttpPost(nodeUri)
        val headerNames = request.getHeaderNames.asScala.filterNot(_ == "Content-Length")
        headerNames.foreach(hdrName => httpPost.setHeader(hdrName, request.getHeader(hdrName)))
        val streamData = getStreamData(request)
        val stringEntity = new StringEntity(streamData)
        httpPost.setEntity(stringEntity)
        val headers = headerNames.map(hdrName => s"$hdrName=${request.getHeader(hdrName)}").mkString(";")
        BWLogger.log(getClass.getName, request.getMethod,
            s"nodeUri: $nodeUri, headers: $headers, input-stream: $streamData", request)
        httpPost
      case other => throw new IllegalArgumentException(s"Bad HTTP operation '$other' found")
    }
    val nodeResponse = HttpClients.createDefault().execute(nodeRequest)
    nodeResponse.getAllHeaders.foreach(hdr => response.addHeader(hdr.getName, hdr.getValue))
    val nodeStatusCode = nodeResponse.getStatusLine.getStatusCode
    response.setStatus(nodeStatusCode)
    if (nodeStatusCode == 200) {
      val nodeEntity = nodeResponse.getEntity
      nodeEntity.writeTo(response.getOutputStream)
    }
    nodeRequest.releaseConnection()
    BWLogger.log(getClass.getName, request.getMethod,
        s"EXIT: (invokeNodeJs=${nodeResponse.getStatusLine.getStatusCode})", request)
  }

  private def handleRequest(request: HttpServletRequest, response: HttpServletResponse,
        delegateTo: Entry.BWServlet => Unit): Unit = {
    if (permitted(request)) {
      val urlParts = request.getRequestURL.toString.split("/")
      val pkgIdx = urlParts.zipWithIndex.find(_._1.matches("api|baf[23]?|dot|etc|graphql|slack|tools|web")).head._2
      val simpleClassName = urlParts(pkgIdx + 1)
      val className = s"com.buildwhiz.${urlParts(pkgIdx)}.$simpleClassName"
      Entry.cache.get(className) match {
        case Some(httpServlet) => delegateTo(httpServlet)
        case None => Try(Class.forName(className)) match {
          case Success(clazz) =>
            Try(clazz.newInstance()) match {
              case Success(httpServlet: Entry.BWServlet @ unchecked) =>
                Entry.cache(className) = httpServlet
                delegateTo(httpServlet)
              case Success(_) => throw new IllegalArgumentException(s"Not a HttpServlet: $className")
              case Failure(t) => throw t
            }
          case Failure(_) =>
            invokeNodeJs(simpleClassName, request, response)
        }
      }
    } else {
      if (request.getCookies == null)
        log(s"ERROR: Authentication failed (No cookies)", request)
      else {
        val cookies = request.getCookies.
          map(c => s"[name:${c.getName} domain:${c.getDomain} path:${c.getPath} value:${c.getValue}]").mkString(", ")
        log(s"ERROR: Authentication failed (cookies: $cookies)", request)
      }
      val uri = request.getRequestURI
      response.sendRedirect("/" + uri.split("/")(1))
    }
  }

  private def handleError(t: Throwable, request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val simpleClassName = t.getClass.getSimpleName
    val className = t.getClass.getName
    val errorMessage = t.getMessage
    log(s"ERROR: $simpleClassName($errorMessage)", request)
    t.printStackTrace()
    val error = new Document("status", "error").append("message", errorMessage).append("source", className)
    response.getWriter.print(error.toJson)
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, response, servlet => servlet.doPost(request, response))
    } catch {
      case t: Throwable =>
        handleError(t, request, response)
    }
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, response, servlet => servlet.doPut(request, response))
    } catch {
      case t: Throwable =>
        log(s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, response, servlet => servlet.doGet(request, response))
    } catch {
      case t: Throwable =>
        handleError(t, request, response)
    }
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, response, servlet => servlet.doDelete(request, response))
    } catch {
      case t: Throwable =>
        log(s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}

object Entry {

  type BWServlet = {def doGet(req: HttpServletRequest, res: HttpServletResponse)
    def doPost(req: HttpServletRequest, res: HttpServletResponse)
    def doPut(req: HttpServletRequest, res: HttpServletResponse)
    def doDelete(req: HttpServletRequest, res: HttpServletResponse)}

  val cache: mutable.Map[String, BWServlet] = mutable.Map.empty[String, BWServlet]
  val sessionCache: mutable.Map[String, HttpSession] = mutable.Map.empty[String, HttpSession]
}
