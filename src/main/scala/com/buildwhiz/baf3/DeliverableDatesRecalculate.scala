package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DeliverableDatesRecalculate extends HttpServlet with HttpUtils {

  private def traverseTrees(phaseRecord: DynDoc): Document = {
    val t0 = System.currentTimeMillis
    val activities = PhaseApi.allActivities(phaseRecord._id[ObjectId])
    val deliverables = DeliverableApi.deliverablesByActivityOids(activities.map(_._id[ObjectId]))
    val deliverableOids = deliverables.map(_._id[ObjectId])
    val query = Map($and -> Seq(Map("owner_deliverable_id" -> Map($in -> deliverableOids)),
        Map("constraint_id" -> Map($in -> deliverableOids))))
    val constraints = BWMongoDB3.constraints.find(query)
    val constraintsByOwnerOid = constraints.groupBy(_.owner_deliverable_id[ObjectId])
    val constraintsByConstraintOid = constraints.groupBy(_.constraint_id[ObjectId])
    val soloDeliverables = deliverables.filter(d => !constraintsByConstraintOid.contains(d._id[ObjectId]) &&
        !constraintsByOwnerOid.contains(d._id[ObjectId]))
    val endDeliverables = deliverables.filter(d => !soloDeliverables.contains(d)).
        filter(d => !constraintsByConstraintOid.contains(d._id[ObjectId]))
    val leafDeliverables = deliverables.filter(d => !soloDeliverables.contains(d)).
        filter(d => !constraintsByOwnerOid.contains(d._id[ObjectId]))
    val delay = System.currentTimeMillis - t0
    val resutlDoc: Document = Map(
        "solo_deliverables" ->
          soloDeliverables.map(d => Map("name" -> d.name[String], "type" -> d.deliverable_type[String])),
        "end_deliverables" ->
          endDeliverables.map(d => Map("name" -> d.name[String], "type" -> d.deliverable_type[String])),
        "leaf_deliverables" ->
          leafDeliverables.map(d => Map("name" -> d.name[String], "type" -> d.deliverable_type[String])),
        "milliseconds" -> delay)
    resutlDoc
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phaseRecord: DynDoc = PhaseApi.phaseById(phaseOid)
      val result = traverseTrees(phaseRecord)
      response.getWriter.print(result.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

