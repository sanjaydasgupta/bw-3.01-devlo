package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProjectApi}
import com.buildwhiz.infra.{BWMongoDB3, BWMongoDB, DynDoc}
import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import com.buildwhiz.infra.DynDoc._

import java.io.PrintWriter
import java.util.{Calendar, TimeZone}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty
import org.camunda.bpm.model.bpmn.instance.{CallActivity, EndEvent, FlowElement, IntermediateCatchEvent, IntermediateThrowEvent, MultiInstanceLoopCharacteristics, Process, StartEvent, Task, TimeDuration, TimerEventDefinition, UserTask}

//import scala.collection.immutable.Seq

object PhaseAdd extends HttpServlet with HttpUtils with BpmnUtils {
//  private implicit def nodeList2nodeSeq(nl: NodeList): Seq[Node] = (0 until nl.getLength).map(nl.item)

//  private def extensionProperties(e: Element, name: String): Seq[Node] = e.getElementsByTagName("camunda:property").
//      filter(nameAttribute(_) == name)
//
  private def extensionProperties2(e: FlowElement, name: String): Seq[CamundaProperty] =
    e.getChildElementsByType(classOf[CamundaProperty]).asScala.toSeq.filter(_.getAttributeValue("name") == name)

  private def cleanText(txt: String): String = txt.replaceAll("\\s+", " ").replaceAll("&#10;", " ")

//  private def getAttribute(node: Node, attributeName: String): String = {
//    node.getAttributes match {
//      case null => ""
//      case attributes: NamedNodeMap => attributes.getNamedItem(attributeName) match {
//        case null => ""
//        case valueItem: Node => valueItem.getTextContent match {
//          case null => ""
//          case textContent: String => textContent
//        }
//      }
//    }
//  }
//
//  private def valueAttribute(node: Node): String = {
//    getAttribute(node, "value")
//  }
//
  private def valueAttribute2(node: CamundaProperty): String = {
    node.getAttributeValue("value")
  }

//  private def nameAttribute(node: Node): String = {
//    getAttribute(node, "name")
//  }
//
//  case class CallerBpmnDom(callerName: String, bpmnName: String, theDom: dom.Document)

//  def bpmnDom(bpmnName: String): dom.Document = {
//    val modelInputStream = getProcessModel(bpmnName)
//    val domParser = new DOMParser()
//    domParser.parse(new InputSource(modelInputStream))
//    domParser.getDocument
//  }
//
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

  private def addTimer(timerNode: IntermediateCatchEvent, bpmnName: String, namePath: String, idPath: String,
      timerBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(timerNode.getName)
    val bpmnId = timerNode.getId
    val timeDuration: TimeDuration = timerNode.getEventDefinitions.asScala.headOption match {
      case Some(ted: TimerEventDefinition) => ted.getTimeDuration
      case _ =>
        val textContent = timerNode.getTextContent
        val elementType = timerNode.getElementType.getTypeName
        val baseType = timerNode.getElementType.getBaseType.getTypeName
        val timerInfo = s"elementType: $elementType, baseType: $baseType, /text: $textContent"
        throw new IllegalArgumentException(s"Not found TimeDuration($bpmnName, '$name', $bpmnId): $timerInfo")
    }

    val timerVariableName = if (timeDuration.getTextContent.matches("\\$\\{.+\\}")) {
      timeDuration.getTextContent.replaceAll("[${}]", "")
    } else {
      val textContent = timeDuration.getTextContent
      val elementType = timeDuration.getElementType.getTypeName
      val baseType = timeDuration.getElementType.getBaseType.getTypeName
      val timerInfo = s"elementType: $elementType, baseType: $baseType, /text: $textContent"
      throw new IllegalArgumentException(s"Bad TimeDuration($bpmnName, '$name', $bpmnId): $timerInfo")
    }
    val duration = extensionProperties2(timerNode, "bw-duration") match {
      case dur +: _ => valueAttribute2(dur)
      case Nil => "00:00:00"
    }
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val timer: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
        "variable" -> timerVariableName,
        "bpmn_id" -> bpmnId, "duration" -> duration, "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
        "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId")
    timerBuffer.append(timer)
  }

  private def addMilestone(milestoneNode: IntermediateThrowEvent, bpmnName: String, namePath: String, idPath: String,
      milestoneBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(milestoneNode.getName)
    val bpmnId = milestoneNode.getId
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val milestone: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
      "bpmn_id" -> bpmnId,
      "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
      "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId")
    milestoneBuffer.append(milestone)
  }

  private def addEndNode(endNode: EndEvent, bpmnName: String, namePath: String, idPath: String,
      endNodeBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(endNode.getName)
    val bpmnId = endNode.getId
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val end: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
      "bpmn_id" -> bpmnId,
      "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
      "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId")
    endNodeBuffer.append(end)
  }

  private def addStartNode(startNode: StartEvent, bpmnName: String, namePath: String, idPath: String,
      startNodeBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(startNode.getName)
    val bpmnId = startNode.getId
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val start: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
      "bpmn_id" -> bpmnId,
      "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
      "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId")
    startNodeBuffer.append(start)
  }

  private def addActivity(bpmnProcessName: String, bpmnProcessCount: Int, activityNode: Task, bpmnName: String,
      namePath: String, idPath: String, timeZone: String, isTakt: Boolean, activityBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(activityNode.getName)
    val bpmnId = activityNode.getId
    val role = extensionProperties2(activityNode, "bw-role") match {
      case r +: _ => valueAttribute2(r)
      //case Nil | null => "phase-manager"
      case Nil | null => "none"
    }
    val description = extensionProperties2(activityNode, "bw-description") match {
      case d +: _ => valueAttribute2(d).replaceAll("\"", "\'")
      case Nil | null => s"$name (no description provided)"
    }
    val bpmnDuration = extensionProperties2(activityNode, "bw-duration") match {
      case dur +: _ => valueAttribute2(dur)
      case Nil | null => "00:00:00"
    }
    val bpmnActualStart = extensionProperties2(activityNode, "bw-actual-start") match {
      case Nil | null => ""
      case start +: _ => valueAttribute2(start)
    }
    val bpmnActualEnd = extensionProperties2(activityNode, "bw-actual-end") match {
      case Nil | null => ""
      case end +: _ => valueAttribute2(end)
    }
    val bpmnScheduledStart = extensionProperties2(activityNode, "bw-scheduled-start") match {
      case Nil | null => ""
      case start +: _ => valueAttribute2(start)
    }
    val bpmnScheduledEnd = extensionProperties2(activityNode, "bw-scheduled-end") match {
      case Nil | null => ""
      case end +: _ => valueAttribute2(end)
    }
    val durations: Document = Map("optimistic" -> -1, "pessimistic" -> -1, "likely" -> -1, "actual" -> -1)
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val atEnd: Boolean = activityNode.getSucceedingNodes.filterByType(classOf[EndEvent]).count() > 0
    val activity: Document = Map("status" -> "defined", "name" -> name, "bpmn_id" -> bpmnId, "at_end" -> atEnd,
        "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId", "is_takt" -> isTakt,
        "bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "role" -> role, "description" -> description,
        "start" -> "00:00:00", "end" -> "00:00:00", "duration" -> bpmnDuration, "durations" -> durations,
        "bpmn_process_name" -> bpmnProcessName, "bpmn_scheduled_start_date" -> date2long(bpmnScheduledStart, timeZone),
        "bpmn_scheduled_end_date" -> date2long(bpmnScheduledEnd, timeZone), "bpmn_process_count" -> bpmnProcessCount,
        "bpmn_actual_start_date" -> date2long(bpmnActualStart, timeZone), "takt_unit_no" -> 1,
        "bpmn_actual_end_date" -> date2long(bpmnActualEnd, timeZone), "on_critical_path" -> false)
    activityBuffer.append(activity)
  }

  private def addVariable(variableNode: CamundaProperty, bpmnName: String, idPath: String,
      variableBuffer: mutable.Buffer[Document]): Unit = {
    val nameAndType = variableNode.getAttributeValue("value")
    val parts = nameAndType.split(":")
    val converters: Map[String, String => Any] =
      Map("B" -> (s => s.toBoolean), "L" -> (s => s.toLong), "D" -> (s => s.toDouble), "S" -> (s => s))
    val (variableName, variableType, defaultValue, label) =
        (parts(0), parts(1), converters(parts(1))(parts(2)), parts(3))
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val variable: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> variableName,
      "type" -> variableType,
      "value" -> defaultValue, "label" -> label)
    variableBuffer.append(variable)
  }

  private def addCallElement(callElementNode: CallActivity, bpmnName: String, idPath: String,
      processName: String, processNameCount: Int, callElementBuffer: mutable.Buffer[Document]): Unit = {
    val isTakt = callElementNode.getLoopCharacteristics match {
      case _: MultiInstanceLoopCharacteristics => true
      case _ => false
    }
    val callee = callElementNode.getCalledElement
    val callerElementId = callElementNode.getId
    val callerElementName = cleanText(callElementNode.getName)
    val fullBpmnName = if (idPath == ".") {
      callerElementId + "/" + callee
    } else {
      idPath.substring(2) + "/" + callerElementId + "/" + callee
    }
    val fullBpmnName2 = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val callDetails: Document = Map("name" -> callee, "bpmn_name_full" -> fullBpmnName, "parent_name" -> bpmnName,
      "parent_activity_id" -> callerElementId, "offset" -> Map("start" -> "00:00:00", "end" -> "00:00:00"),
      "status" -> "defined", "is_takt" -> isTakt, "parent_activity_name" -> callerElementName,
      "bpmn_name_full2" -> fullBpmnName2, "bpmn_process_name" -> processName, "bpmn_process_count" -> processNameCount)
    callElementBuffer.append(callDetails)
  }

  private def analyzeBpmn(bpmnName: String, namePath: String, idPath: String, responseWriter: PrintWriter,
      activityBuffer: mutable.Buffer[Document], timerBuffer: mutable.Buffer[Document],
      milestoneBuffer: mutable.Buffer[Document], endNodeBuffer: mutable.Buffer[Document],
      startNodeBuffer: mutable.Buffer[Document],
      variableBuffer: mutable.Buffer[Document], callElementBuffer: mutable.Buffer[Document],
      timeZone: String, isTakt: Boolean, request: HttpServletRequest): Unit = {
    val level = idPath.split("/").length
    val margin = "&nbsp;&nbsp;&nbsp;|" * level
    //val theDom = bpmnDom(bpmnName)
    //val prefix = theDom.getDocumentElement.getTagName.split(":")(0)
    val bpmnModel = bpmnModelInstance(bpmnName)
    //val bpmnPrefix = bpmnModel.getDocument.getRootElement.getPrefix
    //BWLogger.log(getClass.getName, request.getMethod, s"bpmnPrefix: '$bpmnPrefix'", request)
    val bpmnProcesses: Seq[Process] = bpmnModel.getModelElementsByType(classOf[Process]).asScala.toSeq
    val allTasks: Seq[(Process, Task)] = bpmnProcesses.flatMap(p =>
        (p.getChildElementsByType(classOf[Task]).asScala.toSeq ++
        p.getChildElementsByType(classOf[UserTask]).asScala.toSeq).map(t => (p, t)))

    //val activityNodes: Seq[Element] = (theDom.getElementsByTagName(s"$prefix:userTask") ++
    //    theDom.getElementsByTagName(s"$prefix:task")).map(_.asInstanceOf[Element])
    if (allTasks.nonEmpty) {
      for ((processNode, activityNode) <- allTasks) {
        val name = cleanText(activityNode.getAttributeValue("name"))
        //val name = cleanText(nameAttribute(activityNode))
        val bpmnId = activityNode.getAttributeValue("id")
        //val bpmnId = activityNode.getAttributes.getNamedItem("id").getTextContent
        val bpmnProcessName = processNode.getAttributeValue("name")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) ACTIVITY:$name[$bpmnId]<br/>""")
        }
        addActivity(bpmnProcessName, bpmnProcesses.length, activityNode, bpmnName, namePath, idPath, timeZone,
            isTakt, activityBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-ACTIVITIES<br/>""")
      }
    }

    val timerNodes: Seq[IntermediateCatchEvent] = bpmnModel.getModelElementsByType(classOf[IntermediateCatchEvent]).asScala.toSeq
    if (timerNodes.nonEmpty) {
      for (timerNode <- timerNodes) {
        val name = cleanText(timerNode.getAttributeValue("name"))
        val bpmnId = timerNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) TIMER:$name[$bpmnId]<br/>""")
        }
        addTimer(timerNode, bpmnName, namePath, idPath, timerBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-TIMERS<br/>""")
      }
    }

    val endNodes: Seq[EndEvent] = bpmnModel.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
    if (endNodes.nonEmpty) {
      for (endNode <- endNodes) {
        val name = cleanText(endNode.getAttributeValue("name"))
        val bpmnId = endNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) END:$name[$bpmnId]<br/>""")
        }
        addEndNode(endNode, bpmnName, namePath, idPath, endNodeBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-ENDS<br/>""")
      }
    }

    val startNodes: Seq[StartEvent] = bpmnModel.getModelElementsByType(classOf[StartEvent]).asScala.toSeq
    if (startNodes.nonEmpty) {
      for (startNode <- startNodes) {
        val name = cleanText(startNode.getAttributeValue("name"))
        val bpmnId = startNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) START:$name[$bpmnId]<br/>""")
        }
        addStartNode(startNode, bpmnName, namePath, idPath, startNodeBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-STARTS<br/>""")
      }
    }

    val milestoneNodes: Seq[IntermediateThrowEvent] = bpmnModel.getModelElementsByType(classOf[IntermediateThrowEvent]).
        asScala.toSeq
    if (milestoneNodes.nonEmpty) {
      for (milestoneNode <- milestoneNodes) {
        val name = cleanText(milestoneNode.getAttributeValue("name"))
        val bpmnId = milestoneNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) MILESTONE:$name[$bpmnId]<br/>""")
        }
        addMilestone(milestoneNode, bpmnName, namePath, idPath, milestoneBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-MILESTONES<br/>""")
      }
    }

    val variableNodes: Seq[CamundaProperty] = bpmnModel.getModelElementsByType(classOf[CamundaProperty]).asScala.toSeq.
      filter(_.getAttributeValue("name") == "bw-variable")
    if (variableNodes.nonEmpty) {
      for (variableNode <- variableNodes) {
        val name = cleanText(variableNode.getAttributeValue("name"))
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) VARIABLE:$name[???]<br/>""")
        }
        addVariable(variableNode, bpmnName, idPath, variableBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-VARIABLES<br/>""")
      }
    }

    val processNodeElements: Seq[Process] = bpmnModel.getModelElementsByType(classOf[Process]).asScala.toSeq
    val callActivitiesByProcessNode: Seq[(Process, Seq[CallActivity])] = processNodeElements.
      map(p => (p, p.getChildElementsByType(classOf[CallActivity]).asScala.toSeq.
        filter(_.getAttributeValue("calledElement") != "Infra-Activity-Handler")))
    for ((process, callActivityNodes) <- callActivitiesByProcessNode) {
      val processName = process.getName
      val processNameCount = callActivitiesByProcessNode.length
      if (callActivityNodes.nonEmpty) {
        for (callElementNode <- callActivityNodes) {
          val name = cleanText(callElementNode.getAttributeValue("name"))
          val bpmnId = callElementNode.getAttributeValue("id")
          if (responseWriter != null) {
            responseWriter.println(s"""$margin$namePath($bpmnName) CALL:$name[$bpmnId]<br/>""")
          }
          addCallElement(callElementNode, bpmnName, idPath, processName, processNameCount, callElementBuffer)
        }
      } else {
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) NO-CALLS<br/>""")
        }
      }
    }

    for (call <- callActivitiesByProcessNode.flatMap(_._2)) {
      val calledBpmnName = call.getCalledElement
      val callerElementName = call.getName
      val callerElementId = call.getId
      val newNamePath = s"$namePath/${cleanText(callerElementName)}"
      val newIdPath = s"$idPath/$callerElementId"
      val isTakt2 = call.getLoopCharacteristics match {
        case _: MultiInstanceLoopCharacteristics => true
        case _ => isTakt
      }
      analyzeBpmn(calledBpmnName, newNamePath, newIdPath, responseWriter, activityBuffer, timerBuffer,
        milestoneBuffer, endNodeBuffer, startNodeBuffer, variableBuffer, callElementBuffer, timeZone, isTakt2, request)
    }
  }

  private def addProcess(user: DynDoc, bpmnName: String, processName: String, phaseOid: ObjectId,
      phaseManagerOids: Seq[ObjectId], bwMongoDb: BWMongoDB, flags: Map[String, Boolean], request: HttpServletRequest): Unit = {
    val thePhase = PhaseApi.phaseById(phaseOid, bwMongoDb)
    if (!PersonApi.isBuildWhizAdmin(Right(user)))
      throw new IllegalArgumentException("Not permitted")

    val activityBuffer = mutable.Buffer[Document]()
    val timerBuffer: mutable.Buffer[Document] = mutable.Buffer[Document]()
    val milestoneBuffer = mutable.Buffer[Document]()
    val endNodeBuffer = mutable.Buffer[Document]()
    val startNodeBuffer = mutable.Buffer[Document]()
    val variableBuffer = mutable.Buffer[Document]()
    val callElementBuffer = mutable.Buffer[Document]()

    val phaseTimezone = PhaseApi.timeZone(thePhase, Some(request))
    analyzeBpmn(bpmnName, ".", ".", null, activityBuffer, timerBuffer, milestoneBuffer, endNodeBuffer,
      startNodeBuffer, variableBuffer, callElementBuffer, phaseTimezone, isTakt = false, request)
    val managersInRoles = phaseManagerOids.map(oid =>
      new Document("role_name", "Manager").append("person_id", oid)
    ).asJava

    val newProcess: Document = Map("name" -> processName, "status" -> "defined", "bpmn_name" -> bpmnName,
      "admin_person_id" -> user._id[ObjectId], "process_version" -> -1,
      "timestamps" -> Map("created" -> System.currentTimeMillis), "timers" -> timerBuffer.asJava,
      "variables" -> variableBuffer.asJava, "type" -> "Primary", "unsaved_changes_exist" -> true,
      "bpmn_timestamps" -> callElementBuffer.asJava, "start" -> "00:00:00", "end" -> "00:00:00",
      "assigned_roles" -> managersInRoles, "milestones" -> milestoneBuffer.asJava,
      "end_nodes" -> endNodeBuffer.asJava, "start_nodes" -> startNodeBuffer.asJava)

    bwMongoDb.processes.insertOne(newProcess)
    val processOid = newProcess.y._id[ObjectId]
    val updateResult = bwMongoDb.phases.updateOne(Map("_id" -> phaseOid),
      Map("$push" -> Map("process_ids" -> processOid)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    if (activityBuffer.nonEmpty) {
      bwMongoDb.tasks.insertMany(activityBuffer.asJava)
    }
    val lastActivityOid = activityBuffer.filter(_.y.at_end[Boolean]).
      find(a => a.y.bpmn_name[String] == a.y.bpmn_name_full[String]) match {
      case Some(a) => a.y._id[ObjectId]
      case _ => activityBuffer.find(a => a.y.bpmn_name[String] == a.y.bpmn_name_full[String]) match {
        case Some(a) => a.y._id[ObjectId]
        case _ => activityBuffer.headOption match {
          case Some(a) => a.y._id[ObjectId]
          case _ => throw new IllegalArgumentException("Cant find last activity/task for milestone")
        }
      }
    }
    val activityOids = activityBuffer.map(_.getObjectId("_id")).asJava
    val updateResult2 = bwMongoDb.processes.updateOne(Map("_id" -> processOid),
      Map("$set" -> Map("activity_ids" -> activityOids, "milestone_activity_id" -> lastActivityOid)))
    if (updateResult2.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  private def addTeam(projectOid: ObjectId, personOid: ObjectId, organizationOid: ObjectId, db: BWMongoDB): ObjectId = {
    val ownDocCategories = Seq("Budget", "City-Applications", "City-Approvals", "Contracts", "Deliverables",
      "Del-Specs", "Financial-Applications", "Invoices", "Meeting-Notes", "Progress-Reports", "Specification",
      "Submittals", "Task-Specs", "Work-Scope").map(c => Map("L1" -> c, "_id" -> new ObjectId()))
    val teamRecord: DynDoc = Map("project_id" -> projectOid, "team_name" -> "Default PM Team",
      "organization_id" -> organizationOid, "group" -> "Project Management",
      "skill" -> Seq("Project-Manager (33-25 BW 11)"), "color" -> "#008000",
      "team_members" -> Seq(Map("person_id" -> personOid, "roles" -> Seq("Manager"))),
      "own_doc_categories" -> ownDocCategories, "__v" -> 0)
    db.teams.insertOne(teamRecord.asDoc)
    teamRecord._id[ObjectId]
  }

  private def addPhase(user: DynDoc, phaseName: String, optProjectOid: Option[ObjectId], description: String,
      phaseManagerOids: Seq[ObjectId], db: BWMongoDB, flags: Map[String, Boolean]): ObjectId = {

    val userOid = user._id[ObjectId]
    val isParentProjectManager = optProjectOid match {
      case Some(projectOid) => ProjectApi.isManager(userOid, ProjectApi.projectById(projectOid))
      case None => true
    }
    if (!PersonApi.isBuildWhizAdmin(Right(user)) && !isParentProjectManager)
      throw new IllegalArgumentException("Not permitted")

    val badManagerIds = phaseManagerOids.filterNot(PersonApi.exists(_))
    if (badManagerIds.nonEmpty)
      throw new IllegalArgumentException(s"""Bad project_manager_ids: ${badManagerIds.mkString(", ")}""")

    val managersInRoles = phaseManagerOids.map(oid =>
      new Document("role_name", "Project-Manager").append("person_id", oid)
    ).asJava

    val projectTimeZone = optProjectOid match {
      case Some(projectOid) => ProjectApi.timeZone(ProjectApi.projectById(projectOid))
      case None => "UTC"
    }

    val calendar = Calendar.getInstance()
    val createdMs = calendar.getTimeInMillis
    calendar.setTimeZone(TimeZone.getTimeZone(projectTimeZone))
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    val startEstimatedMs = calendar.getTimeInMillis
    val timestamps = Map("created" -> createdMs, "date_start_estimated" -> startEstimatedMs)
    val basePhaseRecord = Map("name" -> phaseName, "process_ids" -> Seq.empty[ObjectId],
      "assigned_roles" -> managersInRoles, "status" -> "defined",
      "timestamps" -> timestamps, "description" -> description, "tz" -> projectTimeZone)
    val newPhaseRecord = if (phaseManagerOids.nonEmpty) {
      val projectOid = optProjectOid match {
        case Some(projOid) => projOid
        case None => new ObjectId("0" * 24)
      }
      val defaultTeamOid = addTeam(projectOid, phaseManagerOids.head, user.organization_id[ObjectId], db)
      basePhaseRecord ++ Map("team_assignments" -> Seq(Map("team_id" -> defaultTeamOid)),
          "admin_person_id" -> phaseManagerOids.head)
    } else {
      basePhaseRecord ++ Map("team_assignments" -> Seq.empty[Document])
    }
    val insertOneResult = db.phases.insertOne(newPhaseRecord)

    val newPhaseOid = insertOneResult.getInsertedId.asObjectId().getValue
    optProjectOid match {
      case Some(projectOid) =>
        val updateResult = db.projects.updateOne(Map("_id" -> projectOid),
          Map("$addToSet" -> Map("phase_ids" -> newPhaseOid)))
        if (updateResult.getModifiedCount == 0) {
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        }
      case None =>
    }
    newPhaseOid
  }

  def addPhaseWithProcess(user: DynDoc, phaseName: String, optProjectOid: Option[ObjectId], description: String,
      phaseManagerOids: Seq[ObjectId], bpmnName: String, processName: String, db: BWMongoDB,
      flags: Map[String, Boolean], request: HttpServletRequest): ObjectId = {
    BWMongoDB3.withTransaction({
      val phaseOid = addPhase(user, phaseName, optProjectOid, description, phaseManagerOids, db, flags)
      addProcess(user, bpmnName, processName, phaseOid, phaseManagerOids, db, flags, request)
      phaseOid
    })
  }

}

class PhaseAdd extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val responseWriter = response.getWriter
    try {
      val parameters = getParameterMap(request)
      val bpmnName = parameters("bpmn_name")
      response.setContentType("text/html")
      responseWriter.println("<html><tt><br/>")
      val activityBuffer = mutable.Buffer[Document]()
      val timerBuffer: mutable.Buffer[Document] = mutable.Buffer[Document]()
      val milestoneBuffer = mutable.Buffer[Document]()
      val endNodeBuffer = mutable.Buffer[Document]()
      val startNodeBuffer = mutable.Buffer[Document]()
      val variableBuffer = mutable.Buffer[Document]()
      val callElementBuffer = mutable.Buffer[Document]()

      PhaseAdd.analyzeBpmn(bpmnName, ".", ".", responseWriter, activityBuffer, timerBuffer,
        milestoneBuffer, endNodeBuffer, startNodeBuffer, variableBuffer, callElementBuffer, "GMT", isTakt = false, request)
      responseWriter.println(s"<b>Total activities: ${activityBuffer.length}</b><br/>")
      for (activity <- activityBuffer) {
        responseWriter.println(s"${activity.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total timers: ${timerBuffer.length}</b><br/>")
      for (timer <- timerBuffer) {
        responseWriter.println(s"${timer.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total milestones: ${milestoneBuffer.length}</b><br/>")
      for (milestone <- milestoneBuffer) {
        responseWriter.println(s"${milestone.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total end-nodes: ${endNodeBuffer.length}</b><br/>")
      for (endNode <- endNodeBuffer) {
        responseWriter.println(s"${endNode.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total start-nodes: ${startNodeBuffer.length}</b><br/>")
      for (startNode <- startNodeBuffer) {
        responseWriter.println(s"${startNode.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total variable-nodes: ${variableBuffer.length}</b><br/>")
      for (variableNode <- variableBuffer) {
        responseWriter.println(s"${variableNode.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total call-element-nodes: ${callElementBuffer.length}</b><br/>")
      for (callElementNode <- callElementBuffer) {
        responseWriter.println(s"${callElementNode.toJson}<br/>")
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace(responseWriter)
    }
    responseWriter.println("</tt></html><br/>")
    responseWriter.flush()
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val parentProjectOid = new ObjectId(parameters("project_id"))
      if (!ProjectApi.exists(parentProjectOid))
        throw new IllegalArgumentException(s"Unknown project-id: '$parentProjectOid'")
      val user: DynDoc = getUser(request)
      val phaseName = parameters("phase_name")
      PhaseApi.validateNewName(phaseName, parentProjectOid)
      val description = parameters.get("description") match {
        case Some(desc) => desc
        case None => s"No description provided for '$phaseName'"
      }
      val phaseManagerOids: Seq[ObjectId] = parameters.get("manager_ids") match {
        case None => Seq.empty[ObjectId]
        case Some(ids) => ids.split(",").map(_.trim).filter(_.nonEmpty).distinct.map(new ObjectId(_)).toSeq
      }
      val bpmnName = "Phase-" + parameters("bpmn_name")
      val processName = s"$phaseName:$bpmnName"

      PhaseAdd.addPhaseWithProcess(user, phaseName, Some(parentProjectOid), description, phaseManagerOids, bpmnName,
        processName, BWMongoDB3, Map.empty[String, Boolean], request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
    response.getWriter.print(successJson())
    response.setContentType("application/json")
    BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
  }

}