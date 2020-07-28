package com.buildwhiz.web

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.{FileMetadata, GoogleDrive}
import com.buildwhiz.utils.DateTimeUtils

class BrowseDocumentStorage extends HttpServlet with DateTimeUtils {

  private def storageList(request: HttpServletRequest, response: HttpServletResponse, uriRoot: String): Unit = {
    val writer = response.getWriter
    writer.println("<html><head><title>BuildWhiz Amazon S3</title></head>")
    writer.println("<body><h2 align=\"center\">BuildWhiz Amazon S3</h2>")
    val sb = new StringBuilder
    sb.append("<table border=\"1\" align=\"center\">")
    sb.append(List("Created", "Modified", "MIME-Type", "Size", "Key").
      mkString("<tr bgcolor=\"cyan\"><td align=\"center\">", "</td><td align=\"center\">", "</td></tr>"))
    //val objectSummaries: Seq[FileMetadata] = AS3.listObjects
    val objectSummaries: Seq[FileMetadata] = GoogleDrive.listObjects
    val s3ObjectRows = objectSummaries.map(p => {
      val created = dateTimeString(p.createdTime)
      val modified = dateTimeString(p.modifiedTime)
      s"""<tr><td>$created</td><td>$modified</td><td align="center">${p.mimeType}</td>""" +
      s"""<td align="center">${p.size}</td><td align="center">${p.key}</td></tr>"""
    }).mkString
    sb.append(s"$s3ObjectRows</table></html>")
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