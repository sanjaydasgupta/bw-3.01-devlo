package com.buildwhiz.web

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.{FileMetadata, GoogleDriveRepository}
import com.buildwhiz.utils.DateTimeUtils

class BrowseDocumentStorage extends HttpServlet with DateTimeUtils {

  private def storageList(request: HttpServletRequest, response: HttpServletResponse, uriRoot: String): Unit = {
    val writer = response.getWriter
    writer.println("<html><head><title>Document Storage</title></head>")
    writer.println("<body><h2 align=\"center\">Document Storage</h2>")
    val sb = new StringBuilder
    sb.append("<table border=\"1\" align=\"center\">")
    sb.append(List("Created", "Modified", "MIME-Type", "Size", "Key", "Id", "Properties").
      mkString("<tr bgcolor=\"cyan\"><td align=\"center\">", "</td><td align=\"center\">", "</td></tr>"))
    //val objectSummaries: Seq[FileMetadata] = AS3.listObjects
    val objectSummaries: Seq[FileMetadata] = GoogleDriveRepository.listObjects()
    val objectRows = objectSummaries.map(objSummary => {
      val created = dateTimeString(objSummary.createdTime)
      val modified = dateTimeString(objSummary.modifiedTime)
      val properties = objSummary.properties.toSeq.map(pair => s"${pair._1}: ${pair._2}").mkString(", ")
      s"""<tr><td>$created</td><td>$modified</td><td align="center">${objSummary.mimeType}</td>""" +
      s"""<td align="center">${objSummary.size}</td><td align="center">${objSummary.key}</td>""" +
      s"""<td align="center">${objSummary.id}</td><td align="center">$properties</td></tr>"""
    }).mkString
    sb.append(s"$objectRows</table></html>")
    writer.println(sb.toString())
    response.setStatus(HttpServletResponse.SC_OK)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    val className = getClass.getSimpleName
    try {
      request.getRequestURI.split("/").toSeq.reverse match {
        case `className` +: _ => storageList(request, response, className)
        case _ +: `className` +: _ =>
          response.setStatus(HttpServletResponse.SC_OK)
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        writer.println(s"""<span style="background-color: red;">${t.getClass.getSimpleName}(${t.getMessage})</span>""")
        throw t
    }
  }

}