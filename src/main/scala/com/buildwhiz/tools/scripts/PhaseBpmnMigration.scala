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
    if (args.length == 2) {
      val fromPhaseOid = new ObjectId(args(0))
      val toPhaseOid = new ObjectId(args(1))
      val fromActivities = PhaseApi.allActivities(fromPhaseOid)
      val toActivities = PhaseApi.allActivities(toPhaseOid)
      val toActivitiesNameTails = toActivities.map(_.full_path_name[String])
      val toActivitiesMap = toActivitiesNameTails.zip(toActivities).toMap
      response.getWriter.println(s"Activity counts: ${fromActivities.length}, ${toActivities.length}")
      for (fromActivity <- fromActivities) {
        val fromFullPathName = fromActivity.full_path_name[String]
        val found = toActivitiesMap.contains(fromFullPathName)
        if (found) {
          val toActivity = toActivitiesMap(fromFullPathName)
          response.getWriter.println(s"$fromFullPathName --> ${toActivity.full_path_name[String]}")
        } else {
          response.getWriter.println(s"$fromFullPathName --> Nil")
        }
        val deliverables = DeliverableApi.deliverablesByActivityOids(Seq(fromActivity._id[ObjectId]))
        for (d <- deliverables) {
          response.getWriter.println(s"\t${d.name[String]}")
        }
      }
      //BWLogger.log(getClass.getName, "main()", "EXIT-OK", request)
      response.getWriter.println(s"${getClass.getName}:main() EXIT-OK")
    } else {
      response.getWriter.println(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} from-phase-id, to-phase-id")
      //BWLogger.log(getClass.getName, "main()", "EXIT Usage: MigrateProcess from-process-id, to-process-id", request)
    }
  }

}
