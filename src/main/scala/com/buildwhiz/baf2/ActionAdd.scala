package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._

class ActionAdd extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val actionName = parameters("action_name")
      val typ = parameters("type")
      val assigneeOid = new ObjectId(parameters("assignee_id"))
      val bpmnName = parameters("bpmn_name")
      val duration = parameters.getOrElse("duration", "00:00:00")
      val actionDescription = parameters.getOrElse("description", s"$actionName (no description)")
      val optionalRole = parameters.get("assignee_role")
      ActionAdd.add(request, activityOid, actionName, actionDescription, typ, bpmnName, assigneeOid, duration, optionalRole)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object ActionAdd {

  private def getTempDoc(name: String): ObjectId = {
    BWMongoDB3.document_master.find(Map("name" -> name)).asScala.headOption match {
      case Some(doc) => doc.getObjectId("_id")
      case None => val doc = new Document("name", name)
        BWMongoDB3.document_master.insertOne(doc)
        doc.getObjectId("_id")
    }
  }

  private def mainInbox(activityOid: ObjectId, actionName: String): Many[ObjectId] = {
    val activity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
    val mainAction: DynDoc = activity.actions[Many[Document]].find(_.`type`[String] == "main").head
    mainAction.inbox[Many[ObjectId]]
  }

  def add(request: HttpServletRequest, activityOid: ObjectId, actionName: String, actionDescription: String,
          typ: String, bpmnName: String, assigneeOid: ObjectId, duration: String, optRole: Option[String]): Unit = {
    val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
    val existingActionNames: Seq[String] = theActivity.actions[Many[Document]].map(_.name[String])
    if (existingActionNames.contains(actionName))
      throw new IllegalArgumentException(s"Duplicate action name '$actionName'")
    if (!typ.matches("main|prerequisite|review"))
      throw new IllegalArgumentException(s"Bad type value: '$typ'")
    val outbox = new java.util.ArrayList[ObjectId]
    if (typ == "review")
      outbox.asScala.append(getTempDoc(s"$actionName-review-report"))
    val action: Document = Map("name" -> actionName, "description" -> actionDescription, "type" -> typ,
      "status" -> "defined", "inbox" -> mainInbox(activityOid, actionName), "outbox" -> outbox, "duration" -> duration,
      "start" -> "00:00:00", "end" -> "00:00:00", "bpmn_name" -> bpmnName, "assignee_person_id" -> assigneeOid)
    if (optRole.isDefined)
      action.append("assignee_role", optRole.get)
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
      Map("$push" -> Map("actions" -> action)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    val theProcess: DynDoc = ActivityApi.parentProcess(activityOid)
    val (processOid, topLevelBpmn) = (theProcess._id[ObjectId], theProcess.bpmn_name[String])
    ProcessBpmnTraverse.scheduleBpmnElements(topLevelBpmn, processOid, request)
    val actionNameType = s"'${action.y.name[String]}' (${action.y.`type`[String]})"
    BWLogger.audit(getClass.getName, request.getMethod, s"add(): Added action $actionNameType", request)
  }
}
