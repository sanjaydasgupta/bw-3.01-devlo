package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ActionAdd extends HttpServlet with HttpUtils {

  private def getTempDoc(name: String): ObjectId = {
    BWMongoDB3.document_master.find(Map("name" -> name)).asScala.headOption match {
      case Some(doc) => doc.getObjectId("_id")
      case None => val doc = new Document("name", name)
        BWMongoDB3.document_master.insertOne(doc)
        doc.getObjectId("_id")
    }
  }

  private def mainInbox(activityOid: ObjectId, actionName: String): Many[ObjectId] = {
    val activity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).asScala.head
    val mainAction: DynDoc = activity.actions[Many[Document]].find(_.`type`[String] == "main").head
    mainAction.inbox[Many[ObjectId]]
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val actionName = parameters("action_name")
      val typ = parameters("type")
      if (!typ.matches("main|prerequisite|review"))
        throw new IllegalArgumentException(s"Bad type value: '$typ'")
      val bpmnName = parameters("bpmn_name")
      val assigneeOid = new ObjectId(parameters("assignee_id"))
      val outbox = new java.util.ArrayList[ObjectId]
      if (typ == "review")
        outbox.asScala.append(getTempDoc(s"$actionName-review-report"))
      val action: Document = Map("name" -> actionName, "type" -> typ, "status" -> "defined",
        "inbox" -> mainInbox(activityOid, actionName), "outbox" -> outbox, "duration" -> "00:00:00",
        "assignee_person_id" -> assigneeOid, "bpmn_name" -> bpmnName)
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid), Map("$push" -> Map("actions" -> action)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}
