package com.buildwhiz.baf3

import com.buildwhiz.infra.GoogleFolderAdapter
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocCategoriesFromGoogle extends HttpServlet with HttpUtils with MailUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val googleFolderId = parameters("google_folder_id")
      val folderNames = GoogleFolderAdapter.listObjects(googleFolderId).
          filter(_.mimeType == "application/vnd.google-apps.folder").map(_.key)
      val folderNamesJsonArray = folderNames.map(fn => s"\"$fn\"").mkString("[", ", ", "]")
      val returnJson = s"""{"ok": 1, "folders": $folderNamesJsonArray}"""
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
      response.getWriter.print(returnJson)
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
