package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

object DataConsistencyChecker extends HttpUtils {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {

    val respWriter = response.getWriter

    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user))) {
      respWriter.println("Only Admins are permitted")
      throw new IllegalArgumentException("Not permitted")
    }

    val existingProjects: Seq[DynDoc] = BWMongoDB3.projects.find()

    // Phases
    val existingPhases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val oidToExistingPhaseMap: Map[ObjectId, DynDoc] = existingPhases.map(ph => (ph._id[ObjectId], ph)).toMap
    val existingProjectPhaseOidPairs: Seq[(DynDoc, ObjectId)] = existingProjects.
        flatMap(project => project.phase_ids[Many[ObjectId]].map(phaseOid => (project, phaseOid))).
        filter(pp => oidToExistingPhaseMap.contains(pp._2))
    val knownPhaseOids: Set[ObjectId] = existingProjectPhaseOidPairs.map(_._2).toSet
    val (goodPhases, orphanedPhases) = existingPhases.partition(phase => knownPhaseOids.contains(phase._id[ObjectId]))
    respWriter.println("***** Orphaned Phases *****")
    if (orphanedPhases.nonEmpty)
      respWriter.println(orphanedPhases.map(_.asDoc.toJson).mkString("\n"))
    else
      respWriter.println("==== None ====")
    val missingProjectPhaseOidPairs = existingProjectPhaseOidPairs.
        filterNot(ppp => oidToExistingPhaseMap.contains(ppp._2))
    respWriter.println("\n***** Missing Phases OIDs *****")
    if (missingProjectPhaseOidPairs.nonEmpty)
      respWriter.println(missingProjectPhaseOidPairs.
          map(mppp => s"Project '${mppp._1.name[String]} -> phase_id ${mppp._2}'").mkString("\n"))
    else
      respWriter.println("==== None ====")

    // Processes
    val existingProcesses: Seq[DynDoc] = BWMongoDB3.processes.find()
    val oidToExistingProcessMap: Map[ObjectId, DynDoc] = existingProcesses.map(proc => (proc._id[ObjectId], proc)).toMap
    val existingPhaseProcessOidPairs: Seq[(DynDoc, ObjectId)] = goodPhases.
        flatMap(phase => phase.process_ids[Many[ObjectId]].map(processOid => (phase, processOid))).
        filter(pp => oidToExistingProcessMap.contains(pp._2))
    val knownProcessOids: Set[ObjectId] = existingPhaseProcessOidPairs.map(_._2).toSet
    val (goodProcesses, orphanedProcesses) = existingProcesses.partition(process => knownProcessOids.
        contains(process._id[ObjectId]))
    respWriter.println("\n***** Orphaned Processes *****")
    if (orphanedProcesses.nonEmpty)
      respWriter.println(orphanedProcesses.map(_.asDoc.toJson).mkString("\n"))
    else
      respWriter.println("==== None ====")
    val missingPhaseProcessOidPairs = existingPhaseProcessOidPairs.
      filterNot(ppp => oidToExistingProcessMap.contains(ppp._2))
    respWriter.println("\n***** Missing Process OIDs *****")
    if (missingPhaseProcessOidPairs.nonEmpty)
      respWriter.println(missingPhaseProcessOidPairs.
        map(mppp => s"Phase '${mppp._1.name[String]} -> process_id ${mppp._2}'").mkString("\n"))
    else
      respWriter.println("==== None ====")
    val processOidToPhaseOidMap: Map[ObjectId, ObjectId] = existingPhaseProcessOidPairs.
        map(pp => (pp._2, pp._1._id[ObjectId])).toMap

    // Activities (Tasks)
    val existingActivities: Seq[DynDoc] = BWMongoDB3.activities.find()
    val oidToExistingActivitiesMap: Map[ObjectId, DynDoc] = existingActivities.
        map(activity => (activity._id[ObjectId], activity)).toMap
    val existingProcessActivityOidPairs: Seq[(DynDoc, ObjectId)] = goodProcesses.
      flatMap(process => process.activity_ids[Many[ObjectId]].map(activityOid => (process, activityOid))).
      filter(pp => oidToExistingActivitiesMap.contains(pp._2))
    val knownActivityOids: Set[ObjectId] = existingProcessActivityOidPairs.map(_._2).toSet
    val (goodActivities, orphanedActivities) = existingActivities.
        partition(activity => knownActivityOids.contains(activity._id[ObjectId]))
    respWriter.println("\n***** Orphaned Activities *****")
    if (orphanedActivities.nonEmpty)
      respWriter.println(orphanedActivities.map(_.asDoc.toJson).mkString("\n"))
    else
      respWriter.println("==== None ====")
    val missingProcessActivityOidPairs = existingProcessActivityOidPairs.
      filterNot(ppp => oidToExistingActivitiesMap.contains(ppp._2))
    respWriter.println("\n***** Missing Activity OIDs *****")
    if (missingProcessActivityOidPairs.nonEmpty)
      respWriter.println(missingProcessActivityOidPairs.
        map(mppp => s"Process '${mppp._1.name[String]} -> activity_id ${mppp._2}'").mkString("\n"))
    else
      respWriter.println("==== None ====")
    val ativityOidToProcessOidMap: Map[ObjectId, ObjectId] = existingProcessActivityOidPairs.
      map(ap => (ap._2, ap._1._id[ObjectId])).toMap

    // Documents
    val existingDocuments: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("activity_id" -> Map($exists -> true)))
    val (goodDocuments, orphanedDocuments) = existingDocuments.
          partition(doc => oidToExistingActivitiesMap.contains(doc.activity_id[ObjectId]))
    respWriter.println("\n***** Orphaned Documents *****")
    if (orphanedDocuments.nonEmpty)
      respWriter.println(orphanedDocuments.map(_.asDoc.toJson).mkString("\n"))
    else
      respWriter.println("==== None ====")
    val candidateDocuments = goodDocuments.filter(doc => !doc.has("phase_id"))
    respWriter.println("\n***** Candidate Documents *****")
    if (candidateDocuments.nonEmpty)
      respWriter.println(candidateDocuments.map(_.asDoc.toJson).mkString("\n"))
    else
      respWriter.println("==== None ====")

    val candidateDocumentsByActivityId: Map[ObjectId, Seq[DynDoc]] = candidateDocuments.groupBy(_.activity_id[ObjectId])
    val activityOidToPhaseOidMap: Map[ObjectId, ObjectId] = candidateDocumentsByActivityId.keys.
          map(aOid => (aOid, processOidToPhaseOidMap(ativityOidToProcessOidMap(aOid)))).toMap

    if (candidateDocumentsByActivityId.nonEmpty) {
      if (args.length == 1 && args(0) == "go") {
        for (activityOidDocumentSeqPair <- candidateDocumentsByActivityId) {
          val (activityOid: ObjectId, documents: Seq[DynDoc]) = activityOidDocumentSeqPair
          val documentOids = documents.map(_._id[ObjectId])
          val phaseOid = activityOidToPhaseOidMap(activityOid)
          val updateResult = BWMongoDB3.document_master.
                updateMany(Map("_id" -> Map($in -> documentOids)), Map($set -> Map("phase_id" -> phaseOid)))
        }
      }
    } else {
      respWriter.println("No documents to process - exiting")
    }
  }
}
