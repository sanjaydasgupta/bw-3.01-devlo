package com.buildwhiz.baf

import java.util
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import com.sun.org.apache.xerces.internal.parsers.DOMParser
import org.bson.Document
import org.bson.types.ObjectId
import org.w3c.dom
import org.w3c.dom.{Element, Node, NodeList}
import org.xml.sax.InputSource

import scala.collection.JavaConverters._
import scala.language.implicitConversions

class PhaseAdd extends HttpServlet with HttpUtils with BpmnUtils {

  private implicit def nodeList2nodeSeq(nl: NodeList): Seq[Node] = (0 until nl.getLength).map(nl.item)

  private def validatePhase(namesAndDoms: Seq[(String, dom.Document)]): Seq[String] = {

    def validateBpmn(name: String, processDom: dom.Document): Seq[String] = {
      val prefix = processDom.getDocumentElement.getTagName.split(":")(0)

      val startEvents: Seq[Element] = processDom.getElementsByTagName(s"$prefix:startEvent").map(_.asInstanceOf[Element])
      val startExtensions: Seq[Element] = startEvents.flatMap(_.getElementsByTagName(s"$prefix:extensionElements")).
        map(_.asInstanceOf[Element])
      val startExecutionListeners = startExtensions.flatMap(_.getElementsByTagName("camunda:executionListener")).
        map(_.asInstanceOf[Element])
      val startOk = startExecutionListeners.exists(listener => (listener.hasAttribute("class") &&
        listener.getAttribute("class") == "com.buildwhiz.jelly.BpmnStart") && (listener.hasAttribute("event") &&
        listener.getAttribute("event") == "end") && startEvents.length == 1 && startExtensions.length == 1 &&
        startExecutionListeners.length == 1)

      val endEvents: Seq[Element] = processDom.getElementsByTagName(s"$prefix:endEvent").map(_.asInstanceOf[Element])
      val endExtensions: Seq[Element] = endEvents.flatMap(_.getElementsByTagName(s"$prefix:extensionElements")).
        map(_.asInstanceOf[Element])
      val endExecutionListeners = endExtensions.flatMap(_.getElementsByTagName("camunda:executionListener")).
        map(_.asInstanceOf[Element])
      val endOk = endExecutionListeners.exists(listener => (listener.hasAttribute("class") &&
        listener.getAttribute("class") == "com.buildwhiz.jelly.BpmnEnd") && (listener.hasAttribute("event") &&
        listener.getAttribute("event") == "start") && endEvents.length == 1 && endExtensions.length == 1 &&
        endExecutionListeners.length == 1)

      (startOk, endOk) match {
        case (true, true) => Nil
        case (true, false) => Seq(s"$name: end")
        case (false, false) => Seq(s"$name: start", s"$name: end")
        case (false, true) => Seq(s"$name: start")
      }
    }
    namesAndDoms.flatMap(nd => validateBpmn(nd._1, nd._2))
  }

  private def getVariableDefinitions(processNameAndDocument: (String, dom.Document)):
      Seq[(String, String, String, Any, String)] = { // bpmn-name, variable-name, type, default-value, label
    BWLogger.log(getClass.getName, "getVariableDefinitions", "ENTRY")
    try {

      val converters: Map[String, String => Any] =
        Map("B" -> (s => s.toBoolean), "L" -> (s => s.toLong), "D" -> (s => s.toDouble))

      def getVariableNameAndType(variableNode: Node): (String, String, String, Any, String) = {
        // bpmn-name, variable-name, type, default-value, label
        val nameAndType = variableNode.getAttributes.getNamedItem("value").getTextContent
        val parts = nameAndType.split(":")
        // variable-name, type, default-value, label
        if (parts.length != 4)
          throw new IllegalArgumentException(s"BAD Variable Specification: $nameAndType")
        if (!converters.contains(parts(1)))
          throw new IllegalArgumentException(s"BAD Variable Type: ${parts(1)}")
        (processNameAndDocument._1, parts(0), parts(1), converters(parts(1))(parts(2)), parts(3))
      }

      val phaseVariableNodes: Seq[Node] = processNameAndDocument._2.getElementsByTagName("camunda:property").
        filter(_.getAttributes.getNamedItem("name").getTextContent == "bw-variable")
      val variableNamesAndTypes = phaseVariableNodes.map(getVariableNameAndType)
      BWLogger.log(getClass.getName, "getVariableDefinitions", s"""EXIT-OK (${variableNamesAndTypes.mkString(", ")})""")
      variableNamesAndTypes
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getVariableDefinitions", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  private def getCallDefinitions(processNameAndDom: (String, dom.Document)): Seq[(String, String, String)] = {
    BWLogger.log(getClass.getName, "getTimerDefinitions", "ENTRY")
    try {

      def callerCalleeAndCalleeId(callNode: Element, prefix: String): (String, String, String) = {
        // caller-bpmn, called-bpmn, called-bpmn-id
        val callee = callNode.getAttributes.getNamedItem("calledElement").getTextContent
        val bpmnId = callNode.getAttributes.getNamedItem("id").getTextContent
        (processNameAndDom._1, callee, bpmnId)
      }

      val prefix = processNameAndDom._2.getDocumentElement.getTagName.split(":")(0)
      val callActivities: Seq[Node] = processNameAndDom._2.getElementsByTagName(s"$prefix:callActivity")
      val subProcCallElements = callActivities.
        filter(_.getAttributes.getNamedItem("calledElement").getTextContent != "Infra-Activity-Handler")
      val subProcessCalls = subProcCallElements.map(n => callerCalleeAndCalleeId(n.asInstanceOf[Element], prefix))
      BWLogger.log(getClass.getName, "getCallerCalleeAndId", s"""EXIT-OK (${subProcessCalls.mkString(", ")})""")
      subProcessCalls
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getCallerCalleeAndId", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  private def getTimerDefinitions(processNameAndDom: (String, dom.Document)): Seq[(String, String, String, String)] = {
    BWLogger.log(getClass.getName, "getTimerDefinitions", "ENTRY")
    try {

      def getNameVariableNameAndId(timerNode: Element, prefix: String): (String, String, String, String) = {
        // bpmn, name, process-variable, id
        val name = timerNode.getAttributes.getNamedItem("name").getTextContent.replaceAll("\\s+", " ").replaceAll("&#10;", " ")
        val bpmnId = timerNode.getAttributes.getNamedItem("id").getTextContent
        val processVariableName = timerNode/*.asInstanceOf[Element]*/.getElementsByTagName(s"$prefix:timeDuration").
          find(n => n.getAttributes.getNamedItem("xsi:type").getTextContent == s"$prefix:tFormalExpression" &&
          n.getTextContent.matches("\\$\\{.+\\}")).map(_.getTextContent.replaceAll("[\\$\\{\\}]", "")).head
        (processNameAndDom._1, name, processVariableName, bpmnId)
      }

      val prefix = processNameAndDom._2.getDocumentElement.getTagName.split(":")(0)
      val phaseTimerNodes: Seq[Element] = processNameAndDom._2.getElementsByTagName(s"$prefix:intermediateCatchEvent").
        filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition")).map(_.asInstanceOf[Element])
      val timerNamesAndVariables = phaseTimerNodes.map(n => getNameVariableNameAndId(n, prefix))
      BWLogger.log(getClass.getName, "getTimerDefinitions", s"""EXIT-OK (${timerNamesAndVariables.mkString(", ")})""")
      timerNamesAndVariables
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getTimerDefinitions", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  private def getActivityNameRoleDescriptionAndId(processNameAndDom: (String, dom.Document)):
      Seq[(String, String, String, String, String)] = {
    // bpmn, activity-name, role, description, id
    BWLogger.log(getClass.getName, "getActivityNamesAndRoles", "ENTRY")
    try {
      def sequence(callActivity: Element): Int = callActivity.getElementsByTagName("camunda:property").
        find(_.getAttributes.getNamedItem("name").getTextContent == "bw-sequence").
        map(_.getAttributes.getNamedItem("value").getTextContent).head.toInt
      def getNameRoleAndDescription(callActivity: Element): (String, String, String, String, String) = {
        //val name = callActivity.getAttributes.getNamedItem("name").getTextContent.replaceAll("[\\s-]+", "")
        val name = callActivity.getAttributes.getNamedItem("name").getTextContent.replaceAll("\\s+", " ").replaceAll("&#10;", " ")
        val bpmnId = callActivity.getAttributes.getNamedItem("id").getTextContent
        val role = callActivity/*.asInstanceOf[Element]*/.getElementsByTagName("camunda:property").
          find(_.getAttributes.getNamedItem("name").getTextContent == "bw-role").
          map(_.getAttributes.getNamedItem("value").getTextContent) match {
          case Some(r) => r
          case None => "phase-manager"
        }
        val description = callActivity/*.asInstanceOf[Element]*/.getElementsByTagName("camunda:property").
          find(_.getAttributes.getNamedItem("name").getTextContent == "bw-description").
          map(_.getAttributes.getNamedItem("value").getTextContent) match {
          case Some(d) => d.replaceAll("\"", "\'")
          case None => s"no description provided ($name)"
        }
        (processNameAndDom._1, name, role, description, bpmnId)
      }
      val prefix = processNameAndDom._2.getDocumentElement.getTagName.split(":")(0)
      val bpmnCallActivities: Seq[Element] = processNameAndDom._2.getElementsByTagName(s"$prefix:callActivity").
        map(_.asInstanceOf[Element])
      val buildWhizActivities = bpmnCallActivities.filter(_.getAttributes.getNamedItem("calledElement").
        getTextContent == "Infra-Activity-Handler")
      val activityNamesRolesAndDescriptions = buildWhizActivities.sortWith((a, b) => sequence(a) < sequence(b)).
        map(getNameRoleAndDescription)
      BWLogger.log(getClass.getName, "getActivityNamesAndRoles", s"""EXIT-OK (${activityNamesRolesAndDescriptions.mkString(", ")})""")
      activityNamesRolesAndDescriptions
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getActivityNamesAndRoles", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  private def getBpmnDomByName(processName: String, processDocuments: Seq[(String, dom.Document)] = Seq.empty):
      Seq[(String, dom.Document)] = {

    def nameAndDom(bpmnName: String): (String, dom.Document) = {
      val modelInputStream = getProcessModel(bpmnName)
      val domParser = new DOMParser()
      domParser.parse(new InputSource(modelInputStream))
      (bpmnName, domParser.getDocument)
    }

    BWLogger.log(getClass.getName, "getInvolvedProcesses", "ENTRY")
    try {
      val processNameAndDom = nameAndDom(processName)
      val prefix = processNameAndDom._2.getDocumentElement.getTagName.split(":")(0)
      val callActivities: Seq[Node] = processNameAndDom._2.getElementsByTagName(s"$prefix:callActivity")
      val calledElementNames = callActivities.map(_.getAttributes.getNamedItem("calledElement").getTextContent)
      val subProcessNames = calledElementNames.filterNot(_ == "Infra-Activity-Handler")
      val allProcessDocuments = subProcessNames.foldLeft(processDocuments)((docs, name) => getBpmnDomByName(name, docs))
      BWLogger.log(getClass.getName, "getInvolvedProcesses", s"""EXIT-OK (${subProcessNames.mkString(", ")})""")
      processNameAndDom +: allProcessDocuments
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getInvolvedProcesses", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val bpmnName = parameters("bpmn_name")
      val phaseName = parameters("phase_name")
      val projectOid = new ObjectId(parameters("project_id"))
      val adminPersonOid = new ObjectId(parameters("admin_person_id"))
      val allProcessNameAndDoms = getBpmnDomByName(s"Phase-$bpmnName")
      val validationErrors = validatePhase(allProcessNameAndDoms)
      val validationMessage = if (validationErrors.isEmpty) "Validation OK" else s"""Validation ERRORS: ${validationErrors.mkString(", ")}"""
      BWLogger.log(getClass.getName, "doPost", validationMessage, request)
      val variables: Many[Document] = allProcessNameAndDoms.flatMap(getVariableDefinitions).map(kv =>
      {val doc: Document = Map("bpmn_name" -> kv._1, "name" -> kv._2, "type" -> kv._3, "value" -> kv._4, "label" -> kv._5); doc}).asJava
      val timers: Many[Document] = allProcessNameAndDoms.flatMap(getTimerDefinitions).map(kv =>
        {val doc: Document = Map("bpmn_name" -> kv._1, "name" -> kv._2, "variable" -> kv._3, "bpmn_id" -> kv._4,
          "duration" -> "00:00:00", "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined"); doc}).asJava
      val subProcessCalls: Many[Document] = allProcessNameAndDoms.flatMap(getCallDefinitions).map(t => {
        new Document ("parent_name", t._1).append("name", t._2).append("parent_activity_id", t._3).
          append("offset", new Document("start", "00:00:00").append("end", "00:00:00")).append("status", "defined")
      }).asJava
      val newPhase: Document = Map("name" -> phaseName, "status" -> "defined", "bpmn_name" -> s"Phase-$bpmnName",
        "activity_ids" -> new util.ArrayList[ObjectId], "admin_person_id" -> adminPersonOid,
        "timestamps" -> Map("created" -> System.currentTimeMillis), "timers" -> timers, "variables" -> variables,
        "bpmn_timestamps" -> subProcessCalls, "start" -> "00:00:00", "end" -> "00:00:00")
      //BWLogger.log(getClass.getName, "doPost", s"""Timers: ${timers.map(_.name[String]).mkString("[", ", ", "]")}""", request)
      BWMongoDB3.phases.insertOne(newPhase)
      val phaseOid = newPhase.y._id[ObjectId]
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$push" -> Map("phase_ids" -> phaseOid)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val namesRolesAndDescriptions = allProcessNameAndDoms.flatMap(getActivityNameRoleDescriptionAndId)
      //BWLogger.log(getClass.getName, "doPost", s"""Activity-Names-n-Roles: ${namesAndRoles.mkString("[", ", ", "]")}""", request)
      for ((bpmn, activityName, activityRole, activityDescription, bpmnId) <- namesRolesAndDescriptions) {
        // drawings: COVER SHEET, SITE PLAN, BASEMENT FLOOR PLAN
        val availableDocumentList = Seq("57207549d5d8ad331d2ea699", "57207549d5d8ad331d2ea69a",
          "57207549d5d8ad331d2ea69b").map(id => new ObjectId(id))
        val inbox = new java.util.ArrayList[ObjectId]
        inbox.addAll(availableDocumentList.asJava)
        val action: Document = Map("bpmn_name" -> bpmn, "name" -> activityName, "type" -> "main", "status" -> "defined",
          "inbox" -> inbox, "outbox" -> new java.util.ArrayList[ObjectId],
          "assignee_person_id" -> adminPersonOid, "duration" -> "00:00:00", "start" -> "00:00:00", "end" -> "00:00:00")
        val actions = new java.util.ArrayList[Document]
        actions.asScala.append(action)
        val activity: Document = Map("bpmn_name" -> bpmn, "name" -> activityName, "actions" -> actions,
          "status" -> "defined", "bpmn_id" -> bpmnId, "role" -> activityRole, "description" -> activityDescription,
          "start" -> "00:00:00", "end" -> "00:00:00", "duration" -> "00:00:00")
        BWMongoDB3.activities.insertOne(activity)
        val activityOid = activity.getObjectId("_id")
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$push" -> Map("activity_ids" -> activityOid)))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
      val newPhaseDocument = OwnedPhases.processPhase(newPhase, adminPersonOid).asDoc
      response.getWriter.println(bson2json(newPhaseDocument))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}
