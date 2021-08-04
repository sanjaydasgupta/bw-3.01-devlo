package com.buildwhiz

import com.buildwhiz.baf3.NodeConnector

import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, HttpSession}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document

import scala.collection.mutable
import scala.util.{Failure, Success, Try}
import scala.language.reflectiveCalls

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
      case ("MSLogin", "baf3") => true
      case ("Logout", "baf3") => true
      case ("SlackSlashCommand", "slack") => true
      case ("SlackEventCallback", "slack") => true
      case ("SlackInteractiveCallback", "slack") => true
      case _ => false
    }
    try {
      session.getAttribute("bw-user") != null || loggingIn
    } catch {
      case _: Throwable => false
    }
  }

  private def log(event: String, request: HttpServletRequest): Unit = {
    val urlParts = request.getRequestURL.toString.split("/")
    val hasSession = request.getSession(false) != null
    urlParts.zipWithIndex.find(_._1.matches("api|baf[23]?|dot|etc|graphql|slack|tools|web")) match {
      case Some((_, pkgIdx)) =>
        val apiPath = s"${urlParts(pkgIdx)}/${urlParts(pkgIdx + 1)}"
        if (hasSession) {
          BWLogger.log(apiPath, request.getMethod, event, request)
        } else {
          BWLogger.log(apiPath, request.getMethod, event)
        }
      case None =>
        if (hasSession) {
          BWLogger.log("Unknown", request.getMethod, event, request)
        } else {
          BWLogger.log("Unknown", request.getMethod, event)
        }
    }
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
            delegateTo(NodeConnector)
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
      throw new IllegalArgumentException("BuildWhiz: Not logged in")
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

  type BWServlet = {def doGet(req: HttpServletRequest, res: HttpServletResponse): Unit
    def doPost(req: HttpServletRequest, res: HttpServletResponse): Unit
    def doPut(req: HttpServletRequest, res: HttpServletResponse): Unit
    def doDelete(req: HttpServletRequest, res: HttpServletResponse): Unit}

  val cache: mutable.Map[String, BWServlet] = mutable.Map.empty[String, BWServlet]
  val sessionCache: mutable.Map[String, HttpSession] = mutable.Map.empty[String, HttpSession]
}
