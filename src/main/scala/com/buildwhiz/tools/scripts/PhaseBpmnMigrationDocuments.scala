package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.baf3.DeliverableApi
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object PhaseBpmnMigrationDocuments {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.getWriter.println(s"${getClass.getName}:main() ENTRY")
    if (args.length >= 1) {
      val go: Boolean = args.length == 2 && args(1) == "GO"
      val phaseOid = new ObjectId(args(0))
      lazy val projectOid = PhaseApi.parentProject(phaseOid)._id[ObjectId]
      val activities = PhaseApi.allActivities(phaseOid)
      response.getWriter.println(s"Activity count: ${activities.length}")

      for (activity <- activities) {
        response.getWriter.println(s"Activity '${activity.name[String]}' (${activity.full_path_name[String]}) ...")
        val deliverables = DeliverableApi.deliverablesByActivityOids(Seq(activity._id[ObjectId]))
        if (deliverables.nonEmpty) {
          for (deliverable <- deliverables) {
            val documents: Seq[(String, ObjectId)] = Seq("association_document_id", "specfication_document_id").
              flatMap(n => deliverable.get[ObjectId](n).map(oid => (n, oid)))
            if (documents.nonEmpty) {
              if (go) {
                for (document <- documents) {
                  val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> document._2),
                    Map($set -> Map("activity_id" -> activity._id[ObjectId], "phase_id" -> phaseOid, "project_id" -> projectOid)))
                  if (updateResult.getMatchedCount == 0) {
                    throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
                  } else if (updateResult.getModifiedCount == 0) {
                    response.getWriter.println(s"\tDeliverable '${deliverable.name[String]}': ${document._1} FOUND, Not updated")
                  } else {
                    response.getWriter.println(s"\tDeliverable '${deliverable.name[String]}': ${document._1} UPDATED")
                  }
                }
              } else {
                response.getWriter.println(s"""\tDeliverable '${deliverable.name[String]}' has ${documents.map(_._1).mkString(", ")}""")
              }
            } else {
              response.getWriter.println(s"\tDeliverable '${deliverable.name[String]}' has NO documents")
            }
          }
        } else {
          response.getWriter.println("\tNo associated deliverables found")
        }
      }
      response.getWriter.println(s"${getClass.getName}:main() EXIT-OK")
    } else {
      response.getWriter.println(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} phase-id [,GO]")
      //BWLogger.log(getClass.getName, "main()", "EXIT Usage: MigrateProcess from-process-id, to-process-id", request)
    }
  }

}
