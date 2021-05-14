package com.buildwhiz.baf2

import java.util.{Calendar, TimeZone}

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import com.sun.org.apache.xerces.internal.parsers.DOMParser
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId
import org.w3c.dom
import org.w3c.dom.{Element, NamedNodeMap, Node, NodeList}
import org.xml.sax.InputSource

import scala.collection.JavaConverters._
import scala.language.implicitConversions

class ProcessAdd extends HttpServlet with HttpUtils with BpmnUtils {

  private implicit def nodeList2nodeSeq(nl: NodeList): Seq[Node] = (0 until nl.getLength).map(nl.item)

  private def extensionProperties(e: Element, name: String) = e.getElementsByTagName("camunda:property").
    filter(_.getAttributes.getNamedItem("name").getTextContent == name)

  private def validateProcess(namesAndDoms: Seq[(String, dom.Document)]): Seq[String] = {
    //BWLogger.log(getClass.getName, "validateProcess", "ENTRY")

    def validateBpmn(name: String, processDom: dom.Document): Seq[String] = {
      //BWLogger.log(getClass.getName, "validateBpmn", "ENTRY")
      val prefix = processDom.getDocumentElement.getTagName.split(":")(0)

      def executionListeners(e: Element): Seq[Element] = e.getElementsByTagName(s"$prefix:extensionElements").
          flatMap(_.asInstanceOf[Element].getElementsByTagName("camunda:executionListener")).
          map(_.asInstanceOf[Element])

      val startEvents: Seq[Element] = processDom.getElementsByTagName(s"$prefix:startEvent").map(_.asInstanceOf[Element])
      val startExecutionListeners = startEvents.flatMap(executionListeners)
      val startOk = startExecutionListeners.exists(listener => (listener.hasAttribute("class") &&
        listener.getAttribute("class") == "com.buildwhiz.jelly.BpmnStart") && (listener.hasAttribute("event") &&
        listener.getAttribute("event") == "end") && startEvents.length == 1 &&
        startExecutionListeners.length == 1)

      val endEvents: Seq[Element] = processDom.getElementsByTagName(s"$prefix:endEvent").map(_.asInstanceOf[Element])
      val endExecutionListeners = endEvents.flatMap(executionListeners)
      val endOk = endExecutionListeners.exists(listener => (listener.hasAttribute("class") &&
        listener.getAttribute("class") == "com.buildwhiz.jelly.BpmnEnd") && (listener.hasAttribute("event") &&
        listener.getAttribute("event") == "start") && endEvents.length == 1 &&
        endExecutionListeners.length == 1)

      val userTasks: Seq[Element] = processDom.getElementsByTagName(s"$prefix:userTask").
          map(_.asInstanceOf[Element])
      val userTaskStartListeners = userTasks.flatMap(executionListeners)
      val userTaskOk = userTaskStartListeners.forall(listener => (listener.hasAttribute("class") &&
        listener.getAttribute("class") == "com.buildwhiz.jelly.ActivityHandlerStart") &&
        (listener.hasAttribute("event") && listener.getAttribute("event") == "start"))

      val timerNodes: Seq[Element] = processDom.getElementsByTagName(s"$prefix:intermediateCatchEvent").
        filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition")).map(_.asInstanceOf[Element])
      val timerOk = timerNodes.isEmpty || timerNodes.forall(timerNode => {
        val extensionElements: Seq[Element] = timerNode.getElementsByTagName(s"$prefix:extensionElements").
          map(_.asInstanceOf[Element])
        val executionListeners = extensionElements.flatMap(_.getElementsByTagName("camunda:executionListener")).
          map(_.asInstanceOf[Element])
        executionListeners.exists(listener => {
          (listener.hasAttribute("class") && listener.getAttribute("class") == "com.buildwhiz.jelly.TimerTransitions") &&
          (listener.hasAttribute("event") && listener.getAttribute("event") == "start")
        }) && executionListeners.exists(listener => {
          (listener.hasAttribute("class") && listener.getAttribute("class") == "com.buildwhiz.jelly.TimerTransitions") &&
            (listener.hasAttribute("event") && listener.getAttribute("event") == "end")
        }) && extensionElements.length == 1 && executionListeners.length == 2
      })
      //BWLogger.log(getClass.getName, "validateBpmn", "ENTRY")
      Seq(startOk, endOk, timerOk, userTaskOk).zip(Seq("start", "end", "timer", "userTask")).
        filter(!_._1).map(pair => s"$name: ${pair._2}")
    }
    //BWLogger.log(getClass.getName, "validateProcess", "EXIT")
    namesAndDoms.flatMap(nd => validateBpmn(nd._1, nd._2))
  }

  private def getVariableDefinitions(processNameAndDocument: (String, dom.Document)):
      Seq[(String, String, String, Any, String)] = { // bpmn-name, variable-name, type, default-value, label
    //BWLogger.log(getClass.getName, "getVariableDefinitions", "ENTRY")
    try {

      val converters: Map[String, String => Any] =
        Map("B" -> (s => s.toBoolean), "L" -> (s => s.toLong), "D" -> (s => s.toDouble), "S" -> (s => s))

      def getVariableNameAndType(variableNode: Node): (String, String, String, Any, String) = {
        //BWLogger.log(getClass.getName, "getVariableNameAndType", "ENTRY")
        // bpmn-name, variable-name, type, default-value, label
        val nameAndType = variableNode.getAttributes.getNamedItem("value").getTextContent
        val parts = nameAndType.split(":")
        // variable-name, type, default-value, label
        if (parts.length != 4)
          throw new IllegalArgumentException(s"BAD Variable Specification: $nameAndType")
        if (!converters.contains(parts(1)))
          throw new IllegalArgumentException(s"BAD Variable Type: ${parts(1)}")
        //BWLogger.log(getClass.getName, "getVariableNameAndType", "EXIT")
        (processNameAndDocument._1, parts(0), parts(1), converters(parts(1))(parts(2)), parts(3))
      }

      val processVariableNodes: Seq[Node] = processNameAndDocument._2.getElementsByTagName("camunda:property").
        filter(_.getAttributes.getNamedItem("name").getTextContent == "bw-variable")
      val variableNamesAndTypes = processVariableNodes.map(getVariableNameAndType)
      //BWLogger.log(getClass.getName, "getVariableDefinitions", s"""EXIT-OK (${variableNamesAndTypes.mkString(", ")})""")
      variableNamesAndTypes
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getVariableDefinitions", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //t.printStackTrace()
        throw t
    }
  }

  private def getCallDefinitions(processNameAndDom: (String, dom.Document)): Seq[(String, String, String)] = {
    //BWLogger.log(getClass.getName, "getCallDefinitions", "ENTRY")
    try {

      def callerCalleeAndCalleeId(callNode: Element, prefix: String): (String, String, String) = {
        //BWLogger.log(getClass.getName, "callerCalleeAndCalleeId", "ENTRY")
        // caller-bpmn, called-bpmn, called-bpmn-id
        val callee = callNode.getAttributes.getNamedItem("calledElement").getTextContent
        val bpmnId = callNode.getAttributes.getNamedItem("id").getTextContent
        //BWLogger.log(getClass.getName, "callerCalleeAndCalleeId", "EXIT")
        //BWLogger.log(getClass.getName, "callerCalleeAndCalleeId", "EXIT")
        (processNameAndDom._1, callee, bpmnId)
      }

      val prefix = processNameAndDom._2.getDocumentElement.getTagName.split(":")(0)
      val callActivities: Seq[Node] = processNameAndDom._2.getElementsByTagName(s"$prefix:callActivity")
      val subProcCallElements = callActivities.
        filter(_.getAttributes.getNamedItem("calledElement").getTextContent != "Infra-Activity-Handler")
      val subProcessCalls = subProcCallElements.map(n => callerCalleeAndCalleeId(n.asInstanceOf[Element], prefix))
      //BWLogger.log(getClass.getName, "getCallDefinitions", s"""EXIT-OK (${subProcessCalls.mkString(", ")})""")
      subProcessCalls
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getCallerCalleeAndId", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //t.printStackTrace()
        throw t
    }
  }

  private def getTimerDefinitions(processNameAndDom: (String, dom.Document)): Seq[(String, String, String, String, String)] = {
    //BWLogger.log(getClass.getName, "getTimerDefinitions", "ENTRY")
    try {

      def getNameVariableNameAndId(timerNode: Element, prefix: String): (String, String, String, String, String) = {
        //BWLogger.log(getClass.getName, "getNameVariableNameAndId", "ENTRY")
        // bpmn, name, process-variable, id, duration
        val name = timerNode.getAttributes.getNamedItem("name").getTextContent.replaceAll("\\s+", " ").replaceAll("&#10;", " ")
        val bpmnId = timerNode.getAttributes.getNamedItem("id").getTextContent
        val processVariableName = timerNode.getElementsByTagName(s"$prefix:timeDuration").
          find(n => n.getAttributes.getNamedItem("xsi:type").getTextContent == s"$prefix:tFormalExpression" &&
          n.getTextContent.matches("\\$\\{.+\\}")).map(_.getTextContent.replaceAll("[${}]", "")).head
        val duration = extensionProperties(timerNode, "bw-duration") match {
          case dur +: _ => valueAttribute(dur)
          case Nil => "00:00:00"
        }
        //BWLogger.log(getClass.getName, "getNameVariableNameAndId", "EXIT-OK")
        (processNameAndDom._1, name, processVariableName, bpmnId, duration)
      }

      val prefix = processNameAndDom._2.getDocumentElement.getTagName.split(":")(0)
      val processTimerNodes: Seq[Element] = processNameAndDom._2.getElementsByTagName(s"$prefix:intermediateCatchEvent").
        filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition")).map(_.asInstanceOf[Element])
      val timerNamesAndVariables = processTimerNodes.map(n => getNameVariableNameAndId(n, prefix))
      //BWLogger.log(getClass.getName, "getTimerDefinitions", s"""EXIT-OK (${timerNamesAndVariables.mkString(", ")})""")
      timerNamesAndVariables
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getTimerDefinitions", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //t.printStackTrace()
        throw t
    }
  }

  private def valueAttribute(node: Node): String = {
    node.getAttributes match {
      case null => ""
      case attributes: NamedNodeMap => attributes.getNamedItem("value") match {
        case null => ""
        case valueItem: Node => valueItem.getTextContent match {
          case null => ""
          case textContent: String => textContent
        }
      }
    }
  }

  private def getActivityNameRoleDescriptionDurationAndId(processNameAndDom: (String, dom.Document)):
      Seq[(String, String, String, String, String, String, String, String, String, String)] = {
    // bpmn, activity-name, role, description, id
    //BWLogger.log(getClass.getName, "getActivityNameRoleDescriptionDurationAndId", "ENTRY")
    try {

      def sequence(callActivity: Element): Int = callActivity.getElementsByTagName("camunda:property").
        find(_.getAttributes.getNamedItem("name").getTextContent == "bw-sequence").
        map(_.getAttributes.getNamedItem("value").getTextContent) match {
        case None => 0
        case Some(s) => s.toInt
      }

      def trueDuration(actualStart: String, actualEnd: String, schedStart: String, schedEnd: String,
          duration: String): String = {
        //BWLogger.log(getClass.getName, "trueDuration", "ENTRY")
        def dates2duration(start: String, end: String): String = {
          def yyyymmdd2ms(yms: String): Long = {
            val parts = yms.split("[^0-9]+").map(_.toInt)
            val cal = Calendar.getInstance()
            cal.set(parts(0), parts(1) - 1, parts(2))
            cal.getTimeInMillis
          }
          val msDifference = yyyymmdd2ms(end) - yyyymmdd2ms(start)
          val days = msDifference / 86400000L
          val residue = msDifference - days * 86400000L
          val hours = residue / 3600000L
          val minutes = (residue - hours * 3600000L) / 60000L
          f"$days%02d:${hours.toInt}%02d:$minutes%02d"
        }
        val retVal = (actualStart.nonEmpty, actualEnd.nonEmpty, schedStart.nonEmpty, schedEnd.nonEmpty) match {
          case (true, true, _, _) => dates2duration(actualStart, actualEnd)
          case (true, false, _, true) => dates2duration(actualStart, schedEnd)
          case (false, true, true, _) => dates2duration(schedStart, actualEnd)
          case (false, false, true, true) => dates2duration(schedStart, schedEnd)
          case _ => duration
        }
        //BWLogger.log(getClass.getName, "trueDuration", "EXIT")
        retVal
      }

      def getNameRoleDescriptionAndDuration(callActivity: Element):
          (String, String, String, String, String, String, String, String, String, String) = {
        //BWLogger.log(getClass.getName, "getNameRoleDescriptionAndDuration", "ENTRY")
        //val name = callActivity.getAttributes.getNamedItem("name").getTextContent.replaceAll("[\\s-]+", "")
        val name = callActivity.getAttributes.getNamedItem("name").getTextContent.
            replaceAll("\\s+", " ").replaceAll("&#10;", " ")
        val bpmnId = callActivity.getAttributes.getNamedItem("id").getTextContent
        val role = extensionProperties(callActivity, "bw-role") match {
          case r +: _ => valueAttribute(r)
          //case Nil | null => "phase-manager"
          case Nil | null => "none"
        }
        val bpmnDuration = extensionProperties(callActivity, "bw-duration") match {
          case dur +: _ => valueAttribute(dur)
          case Nil | null => "00:00:00"
        }
        val description = extensionProperties(callActivity, "bw-description") match {
          case d +: _ => valueAttribute(d).replaceAll("\"", "\'")
          case Nil | null => s"$name (no description provided)"
        }
        val bpmnActualStart = extensionProperties(callActivity, "bw-actual-start") match {
          case Nil | null => ""
          case start +: _ => valueAttribute(start)
        }
        val bpmnActualEnd = extensionProperties(callActivity, "bw-actual-end") match {
          case Nil | null => ""
          case end +: _ => valueAttribute(end)
        }
        val bpmnScheduledStart = extensionProperties(callActivity, "bw-scheduled-start") match {
          case Nil | null => ""
          case start +: _ => valueAttribute(start)
        }
        val bpmnScheduledEnd = extensionProperties(callActivity, "bw-scheduled-end") match {
          case Nil | null => ""
          case end +: _ => valueAttribute(end)
        }
        val duration = trueDuration(bpmnActualStart, bpmnActualEnd, bpmnScheduledStart, bpmnScheduledEnd,
          bpmnDuration)
        //BWLogger.log(getClass.getName, "getNameRoleDescriptionAndDuration", "EXIT")
        (processNameAndDom._1, name, role, description, duration, bpmnScheduledStart, bpmnScheduledEnd,
            bpmnActualStart, bpmnActualEnd, bpmnId)
      }

      val prefix = processNameAndDom._2.getDocumentElement.getTagName.split(":")(0)
      val bpmnCallActivities: Seq[Element] = processNameAndDom._2.getElementsByTagName(s"$prefix:callActivity").
        map(_.asInstanceOf[Element])
      val bpmnUserTasks: Seq[Element] = processNameAndDom._2.getElementsByTagName(s"$prefix:userTask").
        map(_.asInstanceOf[Element])
      val buildWhizActivities = bpmnCallActivities.filter(_.getAttributes.getNamedItem("calledElement").
        getTextContent == "Infra-Activity-Handler") ++ bpmnUserTasks
      val activityNamesRolesDescriptionsAndDurations = buildWhizActivities.sortWith((a, b) => sequence(a) < sequence(b)).
        map(getNameRoleDescriptionAndDuration)
      //BWLogger.log(getClass.getName, "getActivityNameRoleDescriptionDurationAndId",
      //  s"""EXIT-OK (${activityNamesRolesDescriptionsAndDurations.mkString(", ")})""")
      activityNamesRolesDescriptionsAndDurations
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getActivityNamesAndRoles", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //t.printStackTrace()
        throw t
    }
  }

  private def getBpmnDomByName(processName: String, processDocuments: Seq[(String, dom.Document)] = Seq.empty):
      Seq[(String, dom.Document)] = {
    //BWLogger.log(getClass.getName, "getBpmnDomByName", "ENTRY")
    def nameAndDom(bpmnName: String): (String, dom.Document) = {
      //BWLogger.log(getClass.getName, "nameAndDom", "ENTRY")
      val modelInputStream = getProcessModel(bpmnName)
      //BWLogger.log(getClass.getName, "nameAndDom", "*** Done getProcessModel() ***")
      val domParser = new DOMParser()
      //BWLogger.log(getClass.getName, "nameAndDom", "*** Done new DOMParser() ***")
      domParser.parse(new InputSource(modelInputStream))
      //BWLogger.log(getClass.getName, "nameAndDom", "*** Done domParser.parse() ***")
      val retVal = (bpmnName, domParser.getDocument)
      //BWLogger.log(getClass.getName, "nameAndDom", "EXIT-OK")
      retVal
    }

    //BWLogger.log(getClass.getName, "getInvolvedProcesses", "ENTRY")
    try {
      val processNameAndDom = nameAndDom(processName)
      val prefix = processNameAndDom._2.getDocumentElement.getTagName.split(":")(0)
      val callActivities: Seq[Node] = processNameAndDom._2.getElementsByTagName(s"$prefix:callActivity")
      val calledElementNames = callActivities.map(_.getAttributes.getNamedItem("calledElement").getTextContent)
      val subProcessNames = calledElementNames.filterNot(_ == "Infra-Activity-Handler")
      val allProcessDocuments = subProcessNames.foldLeft(processDocuments)((docs, name) => getBpmnDomByName(name, docs))
      val retVal = processNameAndDom +: allProcessDocuments
      //BWLogger.log(getClass.getName, "getBpmnDomByName", s"""EXIT-OK (${subProcessNames.mkString(", ")})""")
      retVal
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getBpmnDomByName", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //t.printStackTrace()
        throw t
    }
  }

  private def date2long(date: String, timeZone: String): Long = {
    if (date.isEmpty)
      -1L
    else {
      val Array(year, month, day) = date.split("-").map(_.toInt)
      val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone(timeZone))
      calendar.set(year, month - 1, day)
      calendar.getTimeInMillis
    }
  }

  private def doPostTransaction(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val bpmnName = "Phase-" + parameters("bpmn_name")
      val processName = parameters("process_name")
      val phaseOid = new ObjectId(parameters("phase_id"))
      val user: DynDoc = getUser(request)
      if (!PhaseApi.exists(phaseOid))
        throw new IllegalArgumentException(s"Bad phase ID '$phaseOid'")
      val adminPersonOid: ObjectId = parameters.get("admin_person_id") match {
        case None => user._id[ObjectId]
        case Some(id) => new ObjectId(id)
      }
      val thePhase = PhaseApi.phaseById(phaseOid)
      val parentProject = PhaseApi.parentProject(phaseOid)
      if (!PhaseApi.isAdmin(user._id[ObjectId], thePhase) &&
        !ProjectApi.isAdmin(user._id[ObjectId], parentProject) && !PersonApi.isBuildWhizAdmin(Right(user)))
        throw new IllegalArgumentException("Not permitted")

      val allProcessNameAndDoms = getBpmnDomByName(bpmnName)
      val validationErrors = validateProcess(allProcessNameAndDoms)
      val validationMessage = if (validationErrors.isEmpty)
        "Validation OK"
      else
        s"""Validation ERRORS: ${validationErrors.mkString(", ")}"""
      BWLogger.log(getClass.getName, request.getMethod, validationMessage, request)
      val variables: Many[Document] = allProcessNameAndDoms.flatMap(getVariableDefinitions).map(kv =>
      {val doc: Document = Map("bpmn_name" -> kv._1, "name" -> kv._2, "type" -> kv._3, "value" -> kv._4, "label" -> kv._5); doc}).asJava
      val timers: Many[Document] = allProcessNameAndDoms.flatMap(getTimerDefinitions).map(kv =>
      {val doc: Document = Map("bpmn_name" -> kv._1, "name" -> kv._2, "variable" -> kv._3, "bpmn_id" -> kv._4,
        "duration" -> kv._5, "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined"); doc}).asJava
      val subProcessCalls: Many[Document] = allProcessNameAndDoms.flatMap(getCallDefinitions).map(t => {
        new Document ("parent_name", t._1).append("name", t._2).append("parent_activity_id", t._3).
          append("offset", new Document("start", "00:00:00").append("end", "00:00:00")).append("status", "defined")
      }).asJava
      val newProcess: Document = Map("name" -> processName, "status" -> "defined", "bpmn_name" -> bpmnName,
        "activity_ids" -> Seq.empty[ObjectId], "admin_person_id" -> adminPersonOid,
        "timestamps" -> Map("created" -> System.currentTimeMillis), "timers" -> timers, "variables" -> variables,
        "bpmn_timestamps" -> subProcessCalls, "start" -> "00:00:00", "end" -> "00:00:00",
        "assigned_roles" -> Seq.empty[Document])
      BWMongoDB3.processes.insertOne(newProcess)
      val processOid = newProcess.y._id[ObjectId]
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$push" -> Map("process_ids" -> processOid)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val namesRolesAndDescriptions = allProcessNameAndDoms.flatMap(getActivityNameRoleDescriptionDurationAndId)
      for ((bpmn, activityName, activityRole, activityDescription, activityDuration,
      bpmnScheduledStart, bpmnScheduledEnd, bpmnActualStart, bpmnActualEnd, bpmnId) <- namesRolesAndDescriptions) {
        val action: Document = Map("bpmn_name" -> bpmn, "name" -> activityName, "type" -> "main", "status" -> "defined",
          "inbox" -> Seq.empty[ObjectId], "outbox" -> Seq.empty[ObjectId], "assignee_role" -> activityRole,
          "assignee_person_id" -> adminPersonOid, "duration" -> activityDuration,
          "start" -> "00:00:00", "end" -> "00:00:00", "on_critical_path" -> false)
        val timeZone = PhaseApi.timeZone(thePhase)
        val durations: Document = Map("optimistic" -> -1, "pessimistic" -> -1, "likely" -> -1, "actual" -> -1)
        val activity: Document = Map("bpmn_name" -> bpmn, "name" -> activityName, "actions" -> Seq(action),
          "status" -> "defined", "bpmn_id" -> bpmnId, "role" -> activityRole, "description" -> activityDescription,
          "start" -> "00:00:00", "end" -> "00:00:00", "duration" -> activityDuration, "durations" -> durations,
          "bpmn_scheduled_start_date" -> date2long(bpmnScheduledStart, timeZone),
          "bpmn_scheduled_end_date" -> date2long(bpmnScheduledEnd, timeZone),
          "bpmn_actual_start_date" -> date2long(bpmnActualStart, timeZone),
          "bpmn_actual_end_date" -> date2long(bpmnActualEnd, timeZone), "on_critical_path" -> false)
        BWMongoDB3.activities.insertOne(activity)
        val activityOid = activity.getObjectId("_id")
        val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
          Map("$push" -> Map("activity_ids" -> activityOid)))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
      //      BWLogger.log(getClass.getName, request.getMethod,
      //          "Calling ProcessBpmnTraverse.scheduleBpmnElements()", request)
      //      ProcessBpmnTraverse.scheduleBpmnElements(bpmnName, processOid, request)
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, request.getMethod, s"""Added Process ${newProcess.get("name")}""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWMongoDB3.withTransaction(doPostTransaction(request, response))
  }

}
