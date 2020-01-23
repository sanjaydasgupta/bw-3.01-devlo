package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.ProcessApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.HttpServletRequest
import org.bson.types.ObjectId

object MigrateProcess {

  def activityTranslationMap(request: HttpServletRequest, fromProcessOid: ObjectId, toProcessOid: ObjectId):
      Map[ObjectId, ObjectId] = {

    val fromActivities = ProcessApi.allActivities(fromProcessOid).sortBy(_.name[String])
    val toActivities = ProcessApi.allActivities(toProcessOid).sortBy(_.name[String])

    val fromActivityNames = fromActivities.map(_.name[String])
    val toActivityNames = toActivities.map(_.name[String])

    val namesDontMatch = fromActivityNames.zip(toActivityNames).exists(pair => pair._1 != pair._2)
    if (namesDontMatch || fromActivityNames.length != toActivityNames.length) {
      BWLogger.log(getClass.getName, "activityTranslationMap()",
          "Incompatible processes ... Exiting", request)
      throw new IllegalArgumentException("Processes not compatible for migration")
    }

    val fromActivityOids = fromActivities.map(_._id[ObjectId])
    val toActivityOids = toActivities.map(_._id[ObjectId])

    fromActivityOids.zip(toActivityOids).toMap
  }

  def copyAssignments(request: HttpServletRequest, fromProcessOid: ObjectId, toProcessOid: ObjectId): Unit = {

    BWLogger.log(getClass.getName, "copyAssignments", "Calling: activityTranslations()", request)
    val activityTranslations = activityTranslationMap(request, fromProcessOid, toProcessOid)

    val toActivityCount = ProcessApi.allActivities(toProcessOid).length
    val toAssignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("process_id" -> toProcessOid))
    if (toAssignments.exists(_.has("organization_id")) || toAssignments.length > toActivityCount) {
      val errorMessage = "Assigned activities or additional supporting roles exist"
      BWLogger.log(getClass.getName, "copyAssignments", s"$errorMessage ... Exiting", request)
      throw new IllegalArgumentException(errorMessage)
    }
    BWLogger.log(getClass.getName, "copyAssignments",
        s"Verified $toActivityCount nascent entries", request)

    val deleteResult = BWMongoDB3.activity_assignments.deleteMany(Map("process_id" -> toProcessOid))
    val count = BWMongoDB3.activity_assignments.countDocuments(Map("process_id" -> toProcessOid))
    if (toActivityCount != deleteResult.getDeletedCount && count != 0) {
      val errorMessage = s"Delete (${deleteResult.getDeletedCount}) of ($toActivityCount), residue still $count"
      BWLogger.log(getClass.getName, "copyAssignments", s"$errorMessage ... ", request)
      throw new IllegalArgumentException(errorMessage)
    } else {
      BWLogger.log(getClass.getName, "copyAssignments",
          s"Removed all nascent assignment entries", request)
    }


    val fromAssignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("process_id" -> fromProcessOid))
    for (assignment <- fromAssignments) {
      assignment.remove("_id")
      assignment.status = "defined"
      assignment.process_id = toProcessOid
      assignment.activity_id = activityTranslations(assignment.activity_id[ObjectId])
      BWMongoDB3.activity_assignments.insertOne(assignment.asDoc)
    }

    BWLogger.log(getClass.getName, "copyAssignments", s"Created ${fromAssignments.length} assignment entries", request)
  }

  def migrate(request: HttpServletRequest, fromProcessOid: ObjectId, toProcessOid: ObjectId): Unit = {
    if (!ProcessApi.exists(fromProcessOid))
      throw new IllegalArgumentException(s"Bad from-process-id $fromProcessOid")
    if (!ProcessApi.exists(toProcessOid))
      throw new IllegalArgumentException(s"Bad to-process-id $toProcessOid")

    BWLogger.log(getClass.getName, "main()", "Calling: copyAssignments()", request)
    copyAssignments(request, fromProcessOid, toProcessOid)
  }

  def main(request: HttpServletRequest, args: Array[String] = Array.empty[String]): Unit = {
    BWLogger.log(getClass.getName, "main()", "ENTRY", request)
    if (args.length == 2) {
      val fromProcessOid = new ObjectId(args(0))
      val toProcessOid = new ObjectId(args(1))
      BWLogger.log(getClass.getName, "main()", s"Calling: migrate($fromProcessOid, $toProcessOid)", request)
      migrate(request, fromProcessOid, toProcessOid)
      BWLogger.log(getClass.getName, "main()", "EXIT-OK", request)
    } else {
      BWLogger.log(getClass.getName, "main()", "EXIT Usage: MigrateProcess from-process-id, to-process-id", request)
    }
  }

}
