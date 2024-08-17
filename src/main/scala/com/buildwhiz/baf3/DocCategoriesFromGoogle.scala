package com.buildwhiz.baf3

import com.buildwhiz.infra.GoogleFolderAdapter
import com.buildwhiz.infra.DynDoc.Many
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}

import org.bson.Document

import scala.jdk.CollectionConverters._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocCategoriesFromGoogle extends HttpServlet with HttpUtils with MailUtils {

  private def getFolderCategories(folderId: String): (Many[Document], Int) = {
    val children = GoogleFolderAdapter.listObjects(folderId).filter(_.mimeType == "application/vnd.google-apps.folder")
    if (children.nonEmpty) {
      val childNames = children.map(_.key)
      val descendants = children.map(child => getFolderCategories(child.id))
      val count = children.length + descendants.map(_._2).sum
      (childNames.zip(descendants.map(_._1)).map(nd => new Document("name", nd._1).append("children", nd._2)).asJava, count)
    } else {
      (Seq.empty[Document].asJava, 0)
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    response.setContentType("application/json")
    try {
      val t0 = System.currentTimeMillis()
      val googleFolderId = parameters("google_folder_id")
      val (categories, count) = getFolderCategories(googleFolderId)
      val returnJson = new Document("ok", 1).append("folders", categories).toJson
      response.getWriter.print(returnJson)
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms, count: $count)", request)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }
}
