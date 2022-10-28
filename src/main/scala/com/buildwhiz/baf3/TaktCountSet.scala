package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class TaktCountSet extends HttpServlet with HttpUtils {

  private def taktCount(activities: Seq[DynDoc], request: HttpServletRequest): Int = {
    BWLogger.log(getClass.getName, request.getMethod, "taktCount() Entry", request)
    val isTakt: Boolean = activities.forall(_.is_takt[Boolean])
    if (!isTakt)
      throw new IllegalArgumentException("Not a takt block")
    val activitiesByUnit: Map[Int, Seq[DynDoc]] = activities.groupBy(_.takt_unit_no[Int])
    val currentTaktCounts = activitiesByUnit.keys.toSeq.sorted
    if (currentTaktCounts != Seq.range(1, currentTaktCounts.length + 1))
      throw new IllegalArgumentException(s"Bad takt_unit_no values: ${currentTaktCounts.mkString(", ")}")
    val activityCountByUnit: Map[Int, Int] = activitiesByUnit.map(pair => (pair._1, pair._2.length))
    val activityCounts = activityCountByUnit.values.toSeq
    val allActivityCountsEqual = activityCounts.forall(_ == activityCounts.head)
    if (!allActivityCountsEqual)
      throw new IllegalArgumentException(s"Bad activity counts: $activityCounts")
    BWLogger.log(getClass.getName, request.getMethod, s"taktCount() Exit: ${currentTaktCounts.mkString(", ")}", request)
    currentTaktCounts.length
  }

  private def addTaktUnits(phaseOid: ObjectId, activities: Seq[DynDoc], currentCount: Int, newCount: Int,
      request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"addTaktUnits($currentCount -> $newCount) Entry", request)
    val fieldsToKeep = Set("duration", "bpmn_scheduled_end_date", "name", "full_path_name", "role", "description",
      "is_takt", "on_critical_path", "bpmn_actual_start_date", "durations", "bpmn_actual_end_date",
      "bpmn_name_full", "status", "bpmn_scheduled_start_date", "bpmn_id", "end", "start", "full_path_id",
      "bpmn_name", "offset", "bpmn_process_count", "bpmn_process_name")
    val templateDocs = activities.filter(_.takt_unit_no[Int] == 1).map(_.asDoc)
    templateDocs.foreach(templateDoc => {
      val fields = templateDoc.keySet().asScala.toList
      for (field <- fields) {
        if (!fieldsToKeep.contains(field))
          templateDoc.remove(field)
      }
    })

    val newActivityDocs = mutable.Buffer[Document]()
    val newTaktUnitNos = Seq.range(currentCount + 1, newCount + 1)
    for (taktUnitNo <- newTaktUnitNos) {
      for (doc <- templateDocs) {
        val newActivityDoc = new Document(doc)
        newActivityDoc.put("takt_unit_no", taktUnitNo)
        newActivityDocs.append(newActivityDoc)
      }
    }

    BWMongoDB3.tasks.insertMany(newActivityDocs.toSeq)
    val newOids: Many[ObjectId] = newActivityDocs.map(_.getObjectId("_id")).toSeq
    val processOid: ObjectId = PhaseApi.allProcesses(phaseOid).headOption match {
      case Some(procOid) => procOid._id
      case _ => throw new IllegalArgumentException("Unable to find process-oid")
    }
    val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
      Map($addToSet -> Map("activity_ids" -> Map($each -> newOids))))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException("Failed to update activity_ids")
    BWLogger.log(getClass.getName, request.getMethod,
        s"""addTaktUnits() Exit: New 'takt_unit_no' values: ${newTaktUnitNos.mkString(", ")}""", request)
  }

  private def removeTaktUnits(phaseOid: ObjectId, activities: Seq[DynDoc], currentCount: Int, newCount: Int,
      request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"removeTaktUnits($currentCount -> $newCount) Entry", request)
    val oidsToDelete: Many[ObjectId] = activities.filter(_.takt_unit_no[Int] > newCount).map(_._id[ObjectId])
    val processOid: ObjectId = PhaseApi.allProcesses(phaseOid).headOption match {
      case Some(procOid) => procOid._id
      case _ => throw new IllegalArgumentException("Unable to find process-oid")
    }
    val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
      Map($pull -> Map("activity_ids" -> Map($in -> oidsToDelete))))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException("Failed to update activity_ids")
    val deleteResult = BWMongoDB3.tasks.deleteMany(Map("_id" -> Map($in -> oidsToDelete)))
    if (deleteResult.getDeletedCount != oidsToDelete.length)
      throw new IllegalArgumentException(s"Deleted only ${deleteResult.getDeletedCount} activities")
    BWLogger.log(getClass.getName, request.getMethod, s"removeTaktUnits() Exit", request)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val phaseOid = new ObjectId(parameters("phase_id"))
      if (!PhaseApi.exists(phaseOid))
        throw new IllegalArgumentException(s"Bad phase_id: $phaseOid")
      val bpmnNameFull = parameters("bpmn_name_full")
      val bpmnFullPrefix = bpmnNameFull.split("/").init.mkString("/")
      BWLogger.log(getClass.getName, request.getMethod, s"bpmn_name_full prefix: $bpmnFullPrefix", request)
      val activities: Seq[DynDoc] = PhaseApi.allActivities30(Left(phaseOid)).
          filter(a => a.bpmn_name_full[String].startsWith(bpmnFullPrefix))
      if (activities.isEmpty)
        throw new IllegalArgumentException("Bad 'bpmn_name_full' - no activities found")
      val currentTaktCount = taktCount(activities, request)
      val newTaktCount = parameters("count").toInt
      if (newTaktCount < 1)
        throw new IllegalArgumentException(s"Bad count value: $newTaktCount")
      BWMongoDB3.withTransaction({
        if (newTaktCount > currentTaktCount) {
          addTaktUnits(phaseOid, activities, currentTaktCount, newTaktCount, request)
        } else if (newTaktCount < currentTaktCount) {
          removeTaktUnits(phaseOid, activities, currentTaktCount, newTaktCount, request)
        } else {
          BWLogger.log(getClass.getName, request.getMethod, "Both counts equal, exiting", request)
        }
      })
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

