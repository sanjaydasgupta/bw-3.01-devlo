package com.buildwhiz

import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.utils.BWLogger

import scala.util.{Failure, Success, Try}
import scala.language.reflectiveCalls

@MultipartConfig()
class Entry extends HttpServlet {

  private def authenticated(request: HttpServletRequest): Boolean = {
    val session = request.getSession(true)
    val uriParts = request.getRequestURI.split("/")
    val loggingIn = uriParts.last.matches("LoginPost|Environment") && uriParts.init.last == "etc"
    session.getAttribute("bw-user") != null || loggingIn
  }

  private def log(event: String, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getSimpleName, request.getMethod, event, request)
  }

  private def handleRequest(request: HttpServletRequest, response: HttpServletResponse,
        delegateTo: Entry.BWServlet => Unit): Unit = {
    if (authenticated(request)) {
      val urlParts = request.getRequestURL.toString.split("/")
      val pkgIdx = urlParts.zipWithIndex.find(_._1.matches("api|baf|etc|tools|web")).head._2
      val className = s"com.buildwhiz.${urlParts(pkgIdx)}.${urlParts(pkgIdx + 1)}"
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
          case Failure(classNotFound) => throw classNotFound
        }
      }
    } else {
      val uri = request.getRequestURI
      response.sendRedirect("/" + uri.split("/")(1))
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, response, servlet => servlet.doPost(request, response))
    } catch {
      case t: Throwable =>
        log(s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
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
        log(s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
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
  import scala.collection.mutable

  type BWServlet = {def doGet(req: HttpServletRequest, res: HttpServletResponse)
    def doPost(req: HttpServletRequest, res: HttpServletResponse)
    def doPut(req: HttpServletRequest, res: HttpServletResponse)
    def doDelete(req: HttpServletRequest, res: HttpServletResponse)}

  val cache: mutable.Map[String, BWServlet] = mutable.Map.empty[String, BWServlet]
}
