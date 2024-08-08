package com.buildwhiz

import com.buildwhiz.baf3.{MediaServer, NodeConnector, reportFatalException}

import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, HttpSession}
import com.buildwhiz.utils.HttpUtils

import scala.collection.mutable
import scala.util.{Failure, Success, Try}
import scala.language.reflectiveCalls

@MultipartConfig()
class Entry extends HttpServlet with HttpUtils {

  private def permitted(request: HttpServletRequest): Boolean = {
    val uriParts = request.getRequestURI.split("/")
    val clientIp = request.getHeader("X-FORWARDED-FOR") match {
      case null => request.getRemoteAddr
      case ips => ips.split(",").head
    }
    val internalCall = clientIp == request.getLocalAddr
    val loggingIn = (uriParts.last, uriParts.init.last, internalCall) match {
      case (_, _, true) => true
      case ("Status", "etc", _) => true
      case ("LoginPost", "etc", _) => true
      case ("Environment", "etc", _) => true
      case ("BIDataConnector", "etc", _) => true
      case ("Login", "baf2", _) => true
      case ("Logout", "baf2", _) => true
      case ("GLogin", "baf3", _) => true
      case ("MSLogin", "baf3", _) => true
      case ("LoginWithSlack", "baf3", _) => true
      case ("Logout", "baf3", _) => true
      case ("SlackSlashCommand", "slack", _) => true
      case ("SlackEventCallback", "slack", _) => true
      case ("SlackInteractiveCallback", "slack", _) => true
      case (_, "media", _) => true
      case _ => false
    }
    try {
      val session: HttpSession = getSessionAlternatives(request)
      session.getAttribute("bw-user") != null || loggingIn
    } catch {
      case _: Throwable => false
    }
  }

  private def handleRequest(request: HttpServletRequest, delegateTo: Entry.BWServlet => Unit,
      response: HttpServletResponse): Unit = {
    if (permitted(request)) {
      val urlParts = request.getRequestURL.toString.split("/")
      val pkgIdx = urlParts.zipWithIndex.find(_._1.matches(
          "api|baf[23]?|dot|etc|graphql|media|slack|tools|web")).head._2
      if (urlParts(pkgIdx) == "media") {
        delegateTo(MediaServer)
      } else {
        val simpleClassName = urlParts(pkgIdx + 1)
        val className = s"com.buildwhiz.${urlParts(pkgIdx)}.$simpleClassName"
        Entry.cache.get(className) match {
          case Some(httpServlet) => delegateTo(httpServlet)
          case None => Try(Class.forName(className)) match {
            case Success(clazz) =>
              Try(clazz.getDeclaredConstructor().newInstance()) match {
                case Success(httpServlet: Entry.BWServlet@unchecked) =>
                  Entry.cache(className) = httpServlet
                  delegateTo(httpServlet)
                case Success(_) => throw new IllegalArgumentException(s"Not a HttpServlet: $className")
                case Failure(t) => throw t
              }
            case Failure(_) =>
              delegateTo(NodeConnector)
          }
        }
      }
    } else {
      val t = if (request.getCookies == null) {
        new IllegalArgumentException(s"BuildWhiz: Not logged in: Authentication failed (No cookies)")
      } else {
        val cookies = request.getCookies.
          map(c => s"[name:${c.getName} domain:${c.getDomain} path:${c.getPath} value:${c.getValue}]").mkString(", ")
        new IllegalArgumentException(s"BuildWhiz: Not logged in: Authentication failed (cookies: $cookies)")
      }
      reportFatalException(t, getClass.getName, request, response)
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, servlet => servlet.doPost(request, response), response)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, servlet => servlet.doPut(request, response), response)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, servlet => servlet.doGet(request, response), response)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, servlet => servlet.doDelete(request, response), response)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }

}

object Entry {

  private type BWServlet = {
    def doGet(req: HttpServletRequest, res: HttpServletResponse): Unit
    def doPost(req: HttpServletRequest, res: HttpServletResponse): Unit
    def doPut(req: HttpServletRequest, res: HttpServletResponse): Unit
    def doDelete(req: HttpServletRequest, res: HttpServletResponse): Unit
  }

  private val cache: mutable.Map[String, BWServlet] = mutable.Map.empty[String, BWServlet]
  val sessionCache: mutable.Map[String, HttpSession] = mutable.Map.empty[String, HttpSession]
}
