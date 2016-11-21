package com.buildwhiz.web

import java.util.{ArrayList => JArrayList}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.mongodb.client.FindIterable
import org.bson.Document

import scala.collection.JavaConverters._
import scala.collection.mutable

class BrowseMongoDB extends HttpServlet {

  private def collectionSchema(request: HttpServletRequest, response: HttpServletResponse, collectionName: String) = {
    def generateSchema(baseName: String, obj: Any, acc: mutable.Map[String, (Int, Set[String])]): Unit = obj match {
      case dd: DynDoc => generateSchema(baseName, dd.asDoc, acc)
      case fi: FindIterable[Document] @unchecked => fi.asScala.foreach(d => generateSchema(baseName, d, acc))
      case list: JArrayList[_] => list.asScala.foreach(d => generateSchema(baseName + "[]", d, acc))
      case document: Document => for ((k, v) <- document.asScala) {
          val typ = v.getClass.getSimpleName
          val newKey = if (baseName.isEmpty) k else s"$baseName.$k"
          if (acc.contains(newKey)) {
            val schema = acc(newKey)
            acc(newKey) = (schema._1 + 1, schema._2 + typ)
          } else {
            acc(newKey) = (1, Set(typ))
          }
          if (v.isInstanceOf[Document] || v.isInstanceOf[JArrayList[_]])
            generateSchema(newKey, v, acc)
        }
      case x =>
        val typ = x.getClass.getSimpleName
        if (acc.contains(baseName)) {
          val schema = acc(baseName)
          acc(baseName) = (schema._1 + 1, schema._2 + typ)
        } else {
          acc(baseName) = (1, Set(typ))
        }
    }
    val writer = response.getWriter
    writer.println(s"<html><head><title>BuildWhiz Schema $collectionName</title></head>")
    writer.println(s"""<body><h2 align="center">Schema '$collectionName'</h2>""")
    writer.println(s"""<body><h3 align="center"><a href="../../${getClass.getSimpleName}" style="font-weight: normal;">Back to Collections</a></h3>""")
    writer.println("<table border=\"1\" align=\"center\">")
    writer.println("""<tr><td align="center">Name</td><td align="center">Type</td><td align="center">Count</td></tr>""")
    val schema = mutable.Map.empty[String, (Int, Set[String])]
    generateSchema("", BWMongoDB3(collectionName).find(), schema)
    for (s <- schema.toSeq.sortWith(_._1 < _._1)) {
      writer.println(s"""<tr><td>${s._1}</td><td>${s._2._2.mkString(", ")}</td><td>${s._2._1}</td></tr>""")
    }
    writer.println("</table></html>")
    response.setStatus(HttpServletResponse.SC_OK)
  }

  private def mongoCollection(request: HttpServletRequest, response: HttpServletResponse, collectionName: String) = {
    val writer = response.getWriter
    writer.println(s"<html><head><title>BuildWhiz Collection $collectionName</title></head>")
    writer.println(s"""<body><h2 align="center">Collection '$collectionName'</h2>""")
    writer.println(s"""<body><h3 align="center"><a href="../${getClass.getSimpleName}" style="font-weight: normal;">Back to Collections</a></h3>""")
    val sb = new StringBuilder
    sb.append("<table border=\"1\" align=\"center\">")
    for (doc <- BWMongoDB3(collectionName).find.asScala) {
      sb.append(s"<tr><td>${doc.toJson}</td></tr>")
    }
    sb.append("</table></html>")
    writer.println(sb.toString())
    response.setStatus(HttpServletResponse.SC_OK)
  }

  private def mongoDatabase(request: HttpServletRequest, response: HttpServletResponse, uriRoot: String) = {
    val writer = response.getWriter
    writer.println("<html><head><title>MongoDB Database</title></head>")
    writer.println("<body><h2 align=\"center\">MongoDB Database Collections</h2>")
    val sb = new StringBuilder
    sb.append("<table border=\"1\" align=\"center\">")
    sb.append(List("Collection", "Count").
      mkString("<tr bgcolor=\"cyan\"><td align=\"center\">", "</td><td align=\"center\">", "</td></tr>"))
    val collectionNames: Seq[String] = BWMongoDB3.collectionNames
    val collectionSizes: Seq[Long] = collectionNames.map(BWMongoDB3(_).count())
    val collectionData = collectionNames.zip(collectionSizes).sortWith(_._1 < _._1)
    val traceLogRows = collectionData.map(p => {
      val collectionRef = s"""<a href="$uriRoot/${p._1}">${p._1}</a>"""
      val schemaRef = s"""(<a href="$uriRoot/schema/${p._1}">schema</a>)"""
      s"""<tr><td>$collectionRef $schemaRef</td><td>${p._2}</td></tr>"""}).mkString
    sb.append(s"$traceLogRows</table></html>")
    writer.println(sb.toString())
    response.setStatus(HttpServletResponse.SC_OK)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    val className = getClass.getSimpleName
    try {
      request.getRequestURI.split("/").toSeq.reverse match {
        case `className` +: _ => mongoDatabase(request, response, className)
        case collectionName +: `className` +: _ => mongoCollection(request, response, collectionName)
        case collectionName +: "schema" +: `className` +: _ => collectionSchema(request, response, collectionName)
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        writer.println(s"""<span style="background-color: red;">${t.getClass.getSimpleName}(${t.getMessage})</span>""")
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        throw t
    }
  }

}