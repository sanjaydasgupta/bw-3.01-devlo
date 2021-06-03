package com.buildwhiz.utils

import com.buildwhiz.baf2.PersonApi

import java.util.{HashMap => JHashMap}
import javax.servlet.http.HttpServletRequest
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.mongodb.client.MongoCollection
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.DelegateExecution

import scala.annotation.tailrec
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

  def log(className: String, methodName: String, eventName: String, de: DelegateExecution): Unit = {
    @tailrec
    def getAncestors(delegateExecution: DelegateExecution, ancestors: Seq[String] = Seq.empty[String]): Seq[String] = {
      delegateExecution.getSuperExecution match {
        case null => ancestors
        case superDelegateExecution =>
          val parentProcess = superDelegateExecution.getProcessDefinitionId
          val parentActivity = superDelegateExecution.getCurrentActivityName
          getAncestors(superDelegateExecution, s"$parentProcess:$parentActivity" +: ancestors)
      }
    }
    val varNames = de.getVariables.asScala
    varNames("CurrentActivityName") = de.getCurrentActivityName
    varNames("ProcessDefinitionId") = de.getProcessDefinitionId
    varNames("EventName") = de.getEventName
    val bpmnAncestors = getAncestors(de)
    if (bpmnAncestors.nonEmpty)
      varNames("BPMN-Ancestors") = bpmnAncestors.mkString("[", ", ", "]")
    log(className, methodName, eventName, varNames.
        map(kv => (kv._1, if (kv._2 == null) "" else kv._2.toString)).toSeq: _*)
  }

  private def userNameAndId(request: HttpServletRequest): String = {
    val user: DynDoc = getUser(request)
    val persona: DynDoc = getPersona(request)
    if (persona._id[ObjectId] == user._id[ObjectId]) {
      s"${PersonApi.fullName(user)} (${user._id[ObjectId]})"
    } else {
      s"${PersonApi.fullName(user)} (${user._id[ObjectId]}) AS ${PersonApi.fullName(persona)} (${persona._id[ObjectId]})"
    }
  }

  def audit(className: String, methodName: String, eventName: String, request: HttpServletRequest): Unit = {
    val nameAndId = userNameAndId(request)
    log(className, methodName, s"AUDIT: $nameAndId => $eventName", request)
  }

  def log(className: String, methodName: String, eventName: String, request: Option[HttpServletRequest]): Unit =
    request match {
      case Some(req) => log(className, methodName, eventName, req)
      case None => log(className, methodName, eventName)
    }

  def log(className: String, methodName: String, eventName: String, request: HttpServletRequest,
          isLogin: Boolean = false): Unit = {
    val parameters = getParameterMap(request)
    val clientIp = request.getHeader("X-FORWARDED-FOR") match {
      case null => request.getRemoteAddr
      case ip => ip
    }
    if (request.getSession(false) == null) {
      parameters("BW-Session-ID") = "None"
    } else {
      parameters("BW-Session-ID") = request.getSession.getId
    }
    if (isLogin) {
      parameters("BW-X-FORWARDED-FOR") = request.getHeader("X-FORWARDED-FOR")
      parameters("BW-User-Agent") = request.getHeader("User-Agent")
    }
    val paramsWithName = getUser(request) match {
      case null => parameters
      case user => parameters ++ Map("u$nm" -> userNameAndId(request))
    }
    val path = request.getRequestURL.toString.split("/+").drop(3).mkString("/")
    val query = request.getQueryString
    log(className, methodName,
      s"""$eventName ($path${if (query == null) "" else "?" + query}) client=$clientIp""",
      paramsWithName.toSeq: _*)
  }

}
