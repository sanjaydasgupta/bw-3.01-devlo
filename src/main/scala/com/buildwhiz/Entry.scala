package com.buildwhiz

import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWLogger

import scala.util.{Failure, Success, Try}
import scala.language.reflectiveCalls

@MultipartConfig()
class Entry extends HttpServlet {

  private def authenticate(request: HttpServletRequest): Unit = {
    val session = request.getSession
    if (session.isNew) {
      val urlParts = request.getRequestURL.toString.split("/")
    } else {
      val bwToken = session.getAttribute("bw_token")
    }
  }

  private def handleRequest(request: HttpServletRequest, delegateTo: Entry.BWServlet => Unit): Unit = {
    val urlParts = request.getRequestURL.toString.split("/")
    val pkgIdx = urlParts.zipWithIndex.find(_._1.matches("baf|api|web")).head._2
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
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, servlet => servlet.doPost(request, response))
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, servlet => servlet.doPut(request, response))
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPut", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, servlet => servlet.doGet(request, response))
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      handleRequest(request, servlet => servlet.doDelete(request, response))
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
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
