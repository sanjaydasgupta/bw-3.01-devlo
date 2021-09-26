package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.baf3.DeliverableApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.BWLogger
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

object PhaseBpmnMigration {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.getWriter.println(s"${getClass.getName}:main() ENTRY")
    if (args.length >= 2) {
      val go: Boolean = args.length == 3 && args(2) == "GO"
      val fromPhaseOid = new ObjectId(args(0))
      val toPhaseOid = new ObjectId(args(1))
      val fromActivities = PhaseApi.allActivities(fromPhaseOid)
      val toActivities = PhaseApi.allActivities(toPhaseOid)
      val toActivitiesFullPathNames = toActivities.map(_.full_path_name[String])
      val toActivitiesMap = toActivitiesFullPathNames.zip(toActivities).toMap
      response.getWriter.println(s"Activity counts: ${fromActivities.length}, ${toActivities.length}")
      for (fromActivity <- fromActivities) {
        val deliverables = DeliverableApi.deliverablesByActivityOids(Seq(fromActivity._id[ObjectId]))
        val fromFullPathName = fromActivity.full_path_name[String]
        val found = toActivitiesMap.contains(fromFullPathName)
        if (found) {
          val toActivity = toActivitiesMap(fromFullPathName)
          response.getWriter.println(s"$fromFullPathName --> ${toActivity.full_path_name[String]}")
          for (d <- deliverables) {
            val activityIds = s"from ${fromActivity._id[ObjectId]} to ${toActivity._id[ObjectId]}"
            if (go) {
              d.remove("_id")
              d.activity_id = toActivity._id[ObjectId]
              //BWMongoDB3.deliverables.insertOne(d.asDoc)
              response.getWriter.println(s"\tCloned deliverable '${d.name[String]}' ($activityIds)")
            } else {
              response.getWriter.println(s"\tCan clone deliverable '${d.name[String]}' ($activityIds)")
            }
          }
        } else if (fromFullPathName.startsWith("DD/")) {
          for (d <- deliverables) {
            val description = d.description[String]
            val prefix = description.split(" ").head.trim()
            toActivitiesMap.find(_._1.matches(s"${prefix}[% ]+DD${fromFullPathName.substring(2)}")) match {
              case Some((_, toActivity)) =>
                response.getWriter.println(s"$fromFullPathName --> ${toActivity.full_path_name[String]}")
                val activityIds = s"from ${fromActivity._id[ObjectId]} to ${toActivity._id[ObjectId]}"
                if (go) {
                  d.remove("_id")
                  d.activity_id = toActivity._id[ObjectId]
                  //BWMongoDB3.deliverables.insertOne(d.asDoc)
                  response.getWriter.println(s"\tCloned deliverable '${d.name[String]}' ($activityIds)")
                } else {
                  response.getWriter.println(s"\tCan clone deliverable '${d.name[String]}' ($activityIds)")
                }
              case None =>
                response.getWriter.println(s"PROBLEM: $fromFullPathName --> Nil")
            }
            response.getWriter.println(s"\tConfusion with '${d.name[String]}'")
          }
        } else if (deliverables.nonEmpty) {
          response.getWriter.println(s"PROBLEM: $fromFullPathName --> Nil")
        } else {
          response.getWriter.println(s"WARNING: $fromFullPathName --> Nil (but no deliverables exist)")
        }
      }
      //BWLogger.log(getClass.getName, "main()", "EXIT-OK", request)
      response.getWriter.println(s"${getClass.getName}:main() EXIT-OK")
    } else {
      response.getWriter.println(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} from-phase-id, to-phase-id [,GO]")
      //BWLogger.log(getClass.getName, "main()", "EXIT Usage: MigrateProcess from-process-id, to-process-id", request)
    }
  }

}
