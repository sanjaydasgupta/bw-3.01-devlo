package com.buildwhiz.baf3

import com.buildwhiz.baf2.{ActivityApi, PhaseApi, ProcessApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class Dependencyinfo extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getTree(deliverable: DynDoc, optConstraint: Option[DynDoc], optParentOid: Option[ObjectId],
      timezone: String, deliverablesByOid: Map[ObjectId, DynDoc], constraintsByOwnerOid: Map[ObjectId, Seq[DynDoc]],
      count: Array[Int]): Document = {
    count(0) += 1
    val document = new Document("name", deliverable.name[String]).append("type", deliverable.deliverable_type[String]).
        append("status", "Planned").append("takt_unit_no", "").append("commit_date", "02/07/2022").
        append("parent", optParentOid match {case Some(p) => p.toString; case None => ""}).append("direction", "")
    optConstraint match {
      case Some(c) => document.append("_id", c._id[ObjectId].toString).
        append("constraint_id", c.constraint_id[ObjectId].toString).
        append("owner_deliverable_id", c.owner_deliverable_id[ObjectId].toString)
      case None => document.append("_id", deliverable._id[ObjectId].toString).
        append("constraint_id", deliverable._id[ObjectId].toString)
    }
    val items: Many[Document] =
        constraintsByOwnerOid.getOrElse(deliverable._id[ObjectId], Seq.empty).map(constraint2 => {
      val deliverable2 = deliverablesByOid(constraint2.constraint_id[ObjectId])
      val parentOid = optConstraint match {
        case Some(c) => c._id[ObjectId]
        case None => deliverable._id[ObjectId]
      }
      getTree(deliverable2, Some(constraint2), Some(parentOid), timezone, deliverablesByOid, constraintsByOwnerOid,
          count)
    })
    document.append("items", items)
    document
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val t0 = System.currentTimeMillis()
    val parameters = getParameterMap(request)
    val deliverableOid = new ObjectId(parameters("deliverable_id"))
    try {
      val activityOid = DeliverableApi.deliverableById(deliverableOid).activity_id[ObjectId]
      val process = ActivityApi.parentProcess(activityOid)
      val phase = ProcessApi.parentPhase(process._id[ObjectId])
      val timezone = PhaseApi.timeZone(phase, Some(request))
      val activityOids = process.activity_ids[Many[ObjectId]]
      val deliverables: Seq[DynDoc] = BWMongoDB3.deliverables.find(Map("activity_id" -> Map($in -> activityOids)))
      val deliverableOids: Many[ObjectId] = deliverables.map(_._id[ObjectId])
      val deliverablesByOid = deliverables.map(d => (d._id[ObjectId], d)).toMap
      val constraints: Seq[DynDoc] = BWMongoDB3.constraints.find(
        Map("owner_deliverable_id" -> Map($in -> deliverableOids)))
      val constraintsByOwnerOid = constraints.groupBy(_.owner_deliverable_id[ObjectId])
      val message = s"deliverables: ${deliverables.length}, constraints: ${constraints.length}"
      BWLogger.log(getClass.getName, request.getMethod, message, request)
      val counts = Array[Int](0)
      val tree = getTree(deliverablesByOid(deliverableOid), None, None, timezone, deliverablesByOid,
          constraintsByOwnerOid, counts)
      val array: Many[Document] = Seq(tree)
      response.getWriter.print(new Document("tree", array).toJson)
      response.setContentType("application/json")
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms, counts: ${counts(0)})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

