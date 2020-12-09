package com.buildwhiz.utils

import com.buildwhiz.Entry

import javax.servlet.http.{HttpServletRequest, HttpSession, Part}
import com.buildwhiz.infra.DynDoc._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source

trait HttpUtils {

  def getSessionAlternatives(request: HttpServletRequest): HttpSession = {
    getParameterMap(request).get("JSESSIONID") match {
      case Some(sessionId) => Entry.sessionCache.getOrElse(sessionId, request.getSession)
      case None => request.getSession
    }
  }

  def getUser(request: HttpServletRequest): Document =
    getSessionAlternatives(request).getAttribute("bw-user").asInstanceOf[Document]

  def getParameterMap(request: HttpServletRequest): mutable.Map[String, String] =
    request.getParameterMap.asScala.map(p => (p._1, p._2.mkString))

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
      case jList: Many[_] => jList.asScala.map(e => obj2str(e)).mkString("[", ", ", "]")
      case _ => obj.toString
    }
    document.asScala.toSeq.map(kv => s""""${kv._1}": ${obj2str(kv._2)}""").mkString("{", ", ", "}").replaceAll("\n", " ")
  }

  def by3(n: Long, acc: Seq[String] = Nil, depth: Int = 0): String = {
    if (n > 999) {
      val acc2 = "%03d".format(n % 1000) +: acc
      val next = n / 1000
      by3(next, acc2, depth + 1)
    } else if (n > 0) {
      val acc2 = "%d".format(n) +: acc
      acc2.mkString(",")
    } else {
      if (depth == 0)
        "0"
      else
        acc.mkString(",")
    }
  }

  def getParts(request: HttpServletRequest): Seq[Part] = request.getContentType match {
    case null => Seq.empty[Part]
    case s if s.startsWith("multipart/form-data") => request.getParts.asScala.toSeq
    case _ => Seq.empty[Part]
  }

}
