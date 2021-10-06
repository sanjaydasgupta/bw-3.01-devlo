package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import org.bson.types.ObjectId
import org.bson.Document

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object PhaseBpmnMigrationActivityDetails {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val writer = response.getWriter
    writer.println(s"${getClass.getName}:main() ENTRY")
    if (args.length >= 2) {
      val go: Boolean = args.length == 3 && args(2) == "GO"
      val fromPhaseOid = new ObjectId(args(0))
      val toPhaseOid = new ObjectId(args(1))
      val fromActivities = PhaseApi.allActivities30(Left(fromPhaseOid))
      val toActivities = PhaseApi.allActivities30(Left(toPhaseOid))
      val toActivitiesFullPathNames = toActivities.map(_.full_path_name[String])
      val toActivitiesMap = toActivitiesFullPathNames.zip(toActivities).toMap
      writer.println(s"Activity counts: ${fromActivities.length}, ${toActivities.length}")
      for (fromActivity <- fromActivities) {
        val fromFullPathName = fromActivity.full_path_name[String]
        val found = toActivitiesMap.contains(fromFullPathName)
        if (found) {
          val toActivity = toActivitiesMap(fromFullPathName)
          writer.println(s"$fromFullPathName --> ${toActivity.full_path_name[String]}")
          val durations = fromActivity.durations[Document]
          if (go) {
            val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> toActivity._id[ObjectId]),
                Map($set -> Map("durations" -> durations)))
            if (updateResult.getMatchedCount == 0)
              throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
            writer.println(s"\tCOPIED detail fields")
          } else if (fromFullPathName.matches("^[CD]D/.+")) {
            writer.println(s"\tCan copy detail fields: ${durations.toJson}")
          }
        } else if (fromFullPathName.matches("^[CD]D/.+")) {
          val alternatePathName = fromFullPathName.replace("CD/", "100% CD/").replace("DD/", "100% DD/")
          val found = toActivitiesMap.contains(alternatePathName)
          if (found) {
            val toActivity = toActivitiesMap(alternatePathName)
            writer.println(s"$fromFullPathName --> ${toActivity.full_path_name[String]}")
            val durations = fromActivity.durations[Document]
            if (go) {
              val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> toActivity._id[ObjectId]),
                Map($set -> Map("durations" -> durations)))
              if (updateResult.getMatchedCount == 0)
                throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
              writer.println(s"\tCOPIED detail fields")
            } else {
              writer.println(s"\tCan copy detail fields: ${durations.toJson}")
            }
          } else {
            writer.println(s"WARNING (NOT FOUND partner activity): $fromFullPathName --> Nil")
          }
        } else {
          writer.println(s"WARNING (NOT FOUND partner activity): $fromFullPathName --> Nil")
        }
      }
      writer.println(s"${getClass.getName}:main() EXIT-OK")
    } else {
      writer.println(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} from-phase-id, to-phase-id [,GO]")
      //BWLogger.log(getClass.getName, "main()", "EXIT Usage: MigrateProcess from-process-id, to-process-id", request)
    }
  }

}
