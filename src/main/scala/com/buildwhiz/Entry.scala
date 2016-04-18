package com.buildwhiz

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, Utils}

import scala.util.{Failure, Success, Try}

class Entry extends HttpServlet with Utils {

  private def getClazz(className: String): Try[Class[_]] = {
    val classNames = Seq("baf", "rest").map(pkg => s"com.buildwhiz.$pkg.$className")
    classNames.foldLeft(Failure(new ClassNotFoundException()).asInstanceOf[Try[Class[_]]])(
      (t, cn) => t match {case Success(_) => t case Failure(_) => Try(Class.forName(cn))})
  }

  private def handleRequest(request: HttpServletRequest, delegateTo: Entry.BWServlet => Unit): Unit = {
    val urlParts = request.getRequestURL.toString.split("/")
    val bafOffset = urlParts.zipWithIndex.find(_._1.matches("baf|api")).head._2
    val className = urlParts(bafOffset + 1)
    Entry.cache.get(className) match {
      case Some(httpServlet) => delegateTo(httpServlet)
      case None => getClazz(className) match {
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

  type BWServlet = {def doGet(req: HttpServletRequest, res: HttpServletResponse);
    def doPost(req: HttpServletRequest, res: HttpServletResponse);
    def doDelete(req: HttpServletRequest, res: HttpServletResponse)}

  val cache = mutable.Map.empty[String, BWServlet]
}
