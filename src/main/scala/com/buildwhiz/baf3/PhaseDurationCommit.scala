package com.buildwhiz.baf3

import com.buildwhiz.baf2.{ActivityApi, PhaseApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PhaseDurationCommit extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData: DynDoc = Document.parse(parameterString)
      if (!postData.has("duration_values"))
        throw new IllegalArgumentException("'duration_values' not provided")
      val durationValues: Seq[DynDoc] = postData.duration_values[Many[Document]]
      val badDurations = durationValues.filter(dv => !(dv.has("activity_id") && dv.has("duration_likely")) &&
          !(dv.has("timer_id") && dv.has("duration")))
      if (badDurations.nonEmpty)
        throw new IllegalArgumentException("found duration_values without expected fields")
      val phaseOid = new ObjectId(postData.phase_id[String])
      val bpmnName = postData.bpmn_name[String]
      val activities = PhaseApi.allActivities(Left(phaseOid), Map("bpmn_name" -> bpmnName))
      val activityByOid = activities.map(a => (a._id[ObjectId], a)).toMap
      val activitiesByBpmnNameFullAndId = activities.groupBy(a => (a.bpmn_name_full[String], a.bpmn_id[String]))
      val groupLengths = activitiesByBpmnNameFullAndId.values.map(_.length).toSeq
      BWLogger.log(getClass.getName, request.getMethod, s"""takt-unit-counts: ${groupLengths.mkString(", ")}""", request)
      for (durationValue <- durationValues) {
        val theProcess = PhaseApi.allProcesses2(phaseOid).headOption match {
          case Some(proc) => proc
          case None => throw new IllegalArgumentException(s"Bad phase: $phaseOid")
        }
        if (durationValue.has("activity_id")) {
          val activityOid = new ObjectId(durationValue.activity_id[String])
          val theActivity = activityByOid(activityOid)
          val partnerActivityOids: Seq[ObjectId] = // multiple takt-partner activities may be found
              activitiesByBpmnNameFullAndId((theActivity.bpmn_name_full[String], theActivity.bpmn_id[String])).
              map(_._id[ObjectId])
          ActivityApi.durationsSet3(partnerActivityOids,
            durationValue.get[String]("duration_optimistic").map(_.toInt),
            durationValue.get[String]("duration_pessimistic").map(_.toInt),
            durationValue.get[String]("duration_likely").map(_.toInt))
        } else if (durationValue.has("timer_id")) {
          TimerDurationSet.set(request, theProcess, Some(durationValue.timer_id[String]), None, bpmnName,
              durationValue.duration[String])
        } else {
          throw new IllegalArgumentException("\"found duration_values without expected fields\"")
        }
        val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> theProcess._id[ObjectId]),
            Map($set -> Map("unsaved_changes_exist" -> true)))
        if (updateResult.getMatchedCount != 1) {
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
        }
      }
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"changed duration of ${durationValues.length} task and/or timer element(s)"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}