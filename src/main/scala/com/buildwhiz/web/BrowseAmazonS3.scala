package com.buildwhiz.web

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.amazonaws.services.s3.model.S3ObjectSummary
import com.buildwhiz.infra.{AmazonS3 => AS3}

import scala.collection.JavaConverters._

class BrowseAmazonS3 extends HttpServlet {

  private def amazonS3Detail(request: HttpServletRequest, response: HttpServletResponse, collectionName: String) = {
    response.setStatus(HttpServletResponse.SC_OK)
  }

  private def amazonS3List(request: HttpServletRequest, response: HttpServletResponse, uriRoot: String) = {
    val writer = response.getWriter
    writer.println("<html><head><title>BuildWhiz Amazon S3</title></head>")
    writer.println("<body><h2 align=\"center\">BuildWhiz Amazon S3</h2>")
    val sb = new StringBuilder
    sb.append("<table border=\"1\" align=\"center\">")
    sb.append(List("Date", "Size", "Key").
      mkString("<tr bgcolor=\"cyan\"><td align=\"center\">", "</td><td align=\"center\">", "</td></tr>"))
    val objectListing = AS3.listObjects
    val objectSummaries: Seq[S3ObjectSummary] = objectListing.getObjectSummaries.asScala
    val s3ObjectRows = objectSummaries.map(p =>
      s"""<tr><td>${p.getLastModified}</td><td align="center">${p.getSize}</td><td>${p.getKey}</td></tr>""").mkString
    sb.append(s"$s3ObjectRows</table></html>")
    writer.println(sb.toString())
    response.setStatus(HttpServletResponse.SC_OK)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    val className = getClass.getSimpleName
    try {
      request.getRequestURI.split("/").toSeq.reverse match {
        case `className` +: _ => amazonS3List(request, response, className)
        case objectName +: `className` +: _ => amazonS3Detail(request, response, objectName)
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        writer.println(s"""<span style="background-color: red;">${t.getClass.getSimpleName}(${t.getMessage})</span>""")
        throw t
    }
  }

}