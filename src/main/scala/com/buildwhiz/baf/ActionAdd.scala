package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ActionAdd extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val actionName = parameters("action_name")
      val typ = parameters("type")
      val assigneeOid = new ObjectId(parameters("assignee_id"))
      val bpmnName = parameters("bpmn_name")
      val duration = parameters.getOrElse("duration", "00:00:00")
      val actionDescription = parameters.getOrElse("description", s"$actionName (no description)")
      ActionAdd.add(request, activityOid, actionName, actionDescription, typ, bpmnName, assigneeOid, duration)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
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
          typ: String, bpmnName: String, assigneeOid: ObjectId, duration: String): Unit = {
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
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
      Map("$push" -> Map("actions" -> action)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    val thePhase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).head
    val (phaseOid, topLevelBpmn) = (thePhase._id[ObjectId], thePhase.bpmn_name[String])
    PhaseBpmnTraverse.scheduleBpmnElements(topLevelBpmn, phaseOid, request)
    val actionNameType = s"'${action.y.name[String]}' (${action.y.`type`[String]})"
    BWLogger.audit(getClass.getName, "add", s"Added action $actionNameType", request)
  }
}
