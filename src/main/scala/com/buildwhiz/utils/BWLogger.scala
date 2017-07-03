package com.buildwhiz.utils

import java.util.{HashMap => JHashMap, Map => JMap}
import javax.servlet.http.HttpServletRequest

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.mongodb.client.MongoCollection
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.DelegateExecution

import scala.collection.JavaConverters._

object BWLogger extends HttpUtils {

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
    varNames.asScala("EventName") = de.getEventName
    val bpmnAncestors = getAncestors(de)
    if (bpmnAncestors.nonEmpty)
      varNames.asScala("BPMN-Ancestors") = bpmnAncestors.mkString("[", ", ", "]")
    log(className, methodName, eventName, varNames)
  }

  def audit(className: String, methodName: String, eventName: String, request: HttpServletRequest): Unit = {
    val user: DynDoc = getUser(request)
    val userNameAndId = f"${user.first_name[String]}%s ${user.last_name[String]}%s (${user._id[ObjectId]}%s)"
    log(className, methodName, s"AUDIT: $userNameAndId => $eventName", request)
  }

  def log(className: String, methodName: String, eventName: String, request: HttpServletRequest): Unit = {
    val parameters = getParameterMap(request)
    val clientIp = request.getHeader("X-FORWARDED-FOR") match {
      case null => request.getRemoteAddr
      case ip => ip
    }
    val paramsWithName = getUser(request) match {
      case null => parameters
      case user => parameters ++ Map("u$nm" -> s"""${user.get("first_name")} ${user.get("last_name")}""")
    }
    if (Seq("entry", "error").exists(eventName.toLowerCase.contains(_))) {
      val path = request.getRequestURL.toString.split("/+").drop(3).mkString("/")
      val query = request.getQueryString
      log(className, methodName,
        s"""$eventName ($path${if (query == null) "" else "?" + query}) client=$clientIp""",
        paramsWithName.toSeq: _*)
    } else {
      log(className, methodName, s"""$eventName client=$clientIp""", paramsWithName.toSeq: _*)
    }
  }

}
