package com.buildwhiz.api

import java.net.URI
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.Utils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConversions._

class Phase extends HttpServlet with Utils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", s"ENTRY", request)
    try {
      //val phasesCollection = BWMongoDB3.phases
      val postData = getStreamData(request)
      val phaseDocument = Document.parse(postData)
      if (!phaseDocument.containsKey("parent_project_id"))
        throw new IllegalArgumentException("No 'parent_project_id' found")
      if (!phaseDocument.get("parent_project_id").isInstanceOf[ObjectId])
        throw new IllegalArgumentException("Type of 'parent_project_id' not ObjectId")
      phaseDocument("status") = "defined" // Initial status on creation
      phaseDocument("timestamps") = new Document("created", System.currentTimeMillis)
      //val parentProjectSelector = Map("_id", phaseDocument.get("parent_project_id"))
      BWMongoDB3.phases.insertOne(phaseDocument)
      //val newPhaseId = phaseDocument.getObjectId("_id")

      BWMongoDB3.projects.updateOne(Map("_id" -> phaseDocument.get("parent_project_id")),
        Map("$push" -> Map("phase_ids" -> phaseDocument.getObjectId("_id"))))
      response.setContentType("text/plain")
      response.getWriter.print(s"${request.getRequestURI}/${phaseDocument.getObjectId("_id")}")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPut(request, response, "Phase", "phases")
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doDelete", s"ENTRY", request)
    try {
      val uriParts = new URI(request.getRequestURI.replace("[", "%5B").replace("]", "%5D")).getPath.split("/").toSeq.reverse
      val phaseOid = uriParts match {
        case idString +: "Phase" +: _ => new ObjectId(idString)
        case _ => throw new IllegalArgumentException("Id not found")
      }
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val activityOids: Seq[ObjectId] = thePhase.activity_ids[ObjectIdList]
      BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> activityOids)))
      BWMongoDB3.phases.deleteOne(Map("_id" -> phaseOid))
      BWMongoDB3.projects.updateMany(new Document(/* optimization possible */),
        Map("$pull" -> Map("phase_ids" -> phaseOid)))
      BWLogger.log(getClass.getName, "doDelete", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doDelete", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Phase", "phases")
  }

}