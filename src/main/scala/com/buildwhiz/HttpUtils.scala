package com.buildwhiz

import javax.servlet.http.HttpServletRequest

import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.io.Source

trait HttpUtils {

  def getParameterMap(request: HttpServletRequest): mutable.Map[String, String] =
    request.getParameterMap.map(p => (p._1, p._2.mkString))

  def getStreamData(request: HttpServletRequest): String = {
    val source = Source.fromInputStream(request.getInputStream)
    source.getLines().mkString("\n")
  }

  def bson2json(document: Document): String = {
    def obj2str(obj: Any): String = obj match {
      case s: String => s""""${s.replaceAll("\"", "\'")}""""
      case oid: ObjectId => s""""${oid.toString}\""""
      case d: Document => bson2json(d)
      case seq: Seq[_] => seq.map(e => obj2str(e)).mkString("[", ", ", "]")
      //case ms: mutable.Seq[_] => ms.map(e => obj2str(e)).mkString("[", ", ", "]")
      case jList: ManyThings => jList.map(e => obj2str(e)).mkString("[", ", ", "]")
      case _ => obj.toString
    }
    document.toSeq.map(kv => s""""${kv._1}": ${obj2str(kv._2)}""").mkString("{", ", ", "}").replaceAll("\n", " ")
  }

}
