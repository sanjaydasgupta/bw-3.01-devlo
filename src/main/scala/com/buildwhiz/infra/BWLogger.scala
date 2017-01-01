package com.buildwhiz.infra

import javax.servlet.http.HttpServletRequest

import com.mongodb.client.MongoCollection
import org.bson.Document
import BWMongoDB3._

import java.util.{HashMap => JHashMap, Map => JMap}
import org.camunda.bpm.engine.delegate.DelegateExecution

import scala.collection.JavaConverters._
import scala.collection.mutable

object BWLogger {

  private val traceLogCollection: MongoCollection[Document] =
    BWMongoDB3.trace_log

  def log(className: String, methodName: String, eventName: String, variables: (String, String)*): Unit = {
    val javaMap = new JHashMap[String, String]()
    variables.map(t => (t._1, if (t._1.matches("(?i).*password.*")) "****" else t._2)).foreach(kv => javaMap.put(kv._1, kv._2))
    val classNamePrefix = "com.buildwhiz."
    val shortClassName = if (className.startsWith(classNamePrefix))
      className.substring(classNamePrefix.length)
    else
      className
    val record: Map[String, Any] = Map("milliseconds" -> System.currentTimeMillis(), "process_id" -> shortClassName,
      "activity_name" -> methodName, "event_name" -> eventName, "variables" -> javaMap)
    traceLogCollection.insertOne(record)
  }

  private def log(className: String, methodName: String, eventName: String, variables: JMap[String, AnyRef]): Unit =
    log(className, methodName, eventName, (if (variables == null) Nil else
      variables.asScala.toSeq.map(p => (p._1, if (p._2 == null) "" else p._2.toString))): _*)

  def log(className: String, methodName: String, eventName: String, de: DelegateExecution): Unit = {
    def getAncestors(delegateExecution: DelegateExecution, ancestors: Seq[String] = Seq.empty[String]): Seq[String] = {
      delegateExecution.getSuperExecution match {
        case null => ancestors
        case superDelegateExecution =>
          val parentProcess = superDelegateExecution.getProcessDefinitionId
          val parentActivity = superDelegateExecution.getCurrentActivityName
          getAncestors(superDelegateExecution, s"$parentProcess:$parentActivity" +: ancestors)
      }
    }
    val varNames = de.getVariables
    varNames.asScala("CurrentActivityName") = de.getCurrentActivityName
    varNames.asScala("ProcessDefinitionId") = de.getProcessDefinitionId
    val bpmnAncestors = getAncestors(de)
    if (bpmnAncestors.nonEmpty)
      varNames.asScala("BPMN-Ancestors") = bpmnAncestors.mkString("[", ", ", "]")
    log(className, methodName, eventName, varNames)
  }

  private def getParameterMap(request: HttpServletRequest): mutable.Map[String, String] =
    request.getParameterMap.asScala.map(p => (p._1, p._2.mkString))

  def log(className: String, methodName: String, eventName: String, request: HttpServletRequest): Unit = {
    val parameters = getParameterMap(request)
    val url = request.getRequestURL.toString
    val clientIp = request.getHeader("X-FORWARDED-FOR") match {
      case null => request.getRemoteAddr
      case ip => ip
    }
    if (eventName.toLowerCase.contains("entry")) {
      val referrer = if (request.getHeaderNames.asScala.contains("referer")) request.getHeader("referer") else "(none)"
      val commonPrefix = url.toList.zip(referrer.toList).takeWhile(p => p._1 == p._2)
      val refPage = referrer.substring(commonPrefix.length)
      val path = request.getServletPath
      val query = request.getQueryString
      log(className, methodName,
        s"""$eventName ($path${if (query == null) "" else "?" + query}) client=$clientIp referrer=$refPage""",
        parameters.toSeq: _*)
    } else {
      log(className, methodName, s"""$eventName client=$clientIp""", parameters.toSeq: _*)
    }
  }

}
