package com.buildwhiz.tools.scripts

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

object DocumentsPhaseIdAdd {

  def findProjectOrphans(response: HttpServletResponse): Seq[DynDoc] = {
    response.getWriter.println("INFO: Looking for document records with Invalid/Missing 'project_id' values ...")
    val allProjects: Seq[DynDoc] = BWMongoDB3.projects.find()
    val allProjectOids: Seq[ObjectId] = allProjects.map(_._id[ObjectId])
    val query = Map($or -> Seq(
        Map("project_id" -> Map($exists -> false)),
        Map("project_id" -> Map($exists -> true), "project_id" -> Map($not -> Map($in -> allProjectOids)))
      )
    )
    val orphanedDocuments: Seq[DynDoc] = BWMongoDB3.document_master.find(query)
    if (orphanedDocuments.nonEmpty) {
      response.getWriter.println(s"WARN: Found ${orphanedDocuments.length} orphaned documents, list follows")
      for (orphan <- orphanedDocuments) {
        val documentName = orphan.get[String]("name") match {
          case None => "Undefined"
          case Some(name) => name
        }
        val projectName = orphan.get[ObjectId]("project_id") match {
          case None => "Undefined"
          case Some(projectOid) => s"Not-Available ($projectOid)"
        }
        response.getWriter.println(s"INFO: ORPHAN Name: '$documentName', Project: '$projectName'")
      }
    } else {
      response.getWriter.println("INFO: Found NO orphaned documents")
    }
    orphanedDocuments
  }

  def findActivityOrphans(allActivityOids: Seq[ObjectId], response: HttpServletResponse): Seq[DynDoc] = {
    response.getWriter.println("INFO: Looking for document records with Invalid 'activity_id' values ...")

    val orphanedDocuments = BWMongoDB3.document_master.find(Map("activity_id" -> Map($exists -> true),
      "activity_id" -> Map($not -> Map($in -> allActivityOids))))
    if (orphanedDocuments.nonEmpty) {
      response.getWriter.println(s"WARN: Found ${orphanedDocuments.length} orphaned documents, list follows")
      for (orphan <- orphanedDocuments) {
        val documentName = orphan.name[String]
        val projectName = orphan.get[ObjectId]("project_id") match {
          case None => "Undefined"
          case Some(projectOid) => BWMongoDB3.projects.find(Map("_id" -> projectOid)).headOption match {
            case None => s"Not-Available ($projectOid)"
            case Some(project) => project.name[String]
          }
        }
        val phaseName = orphan.get[ObjectId]("phase_id") match {
          case None => "Undefined"
          case Some(phaseOid) => BWMongoDB3.phases.find(Map("_id" -> phaseOid)).headOption match {
            case None => s"Not-Available ($phaseOid)"
            case Some(phase) => phase.name[String]
          }
        }
        response.getWriter.println(s"INFO: ORPHAN Name: '$documentName', Project: '$projectName', Phase: '$phaseName'")
      }
    } else {
      response.getWriter.println("INFO: Found NO orphaned documents")
    }
    orphanedDocuments
  }

  def findCandidateRecords(allActivityOids: Seq[ObjectId], response: HttpServletResponse): Seq[DynDoc] = {
    response.getWriter.println("INFO: Looking for document records with a VALID 'activity_id', but NO 'phase_id' ...")

    val candidateDocuments = BWMongoDB3.document_master.find(Map("activity_id" -> Map($exists -> true),
      "activity_id" -> Map($in -> allActivityOids), "phase_id" -> Map($exists -> false)))
    if (candidateDocuments.nonEmpty) {
      response.getWriter.println(s"INFO: Found ${candidateDocuments.length} orphaned documents, list follows")
      for (candidate <- candidateDocuments) {
        val documentName = candidate.name[String]
        val projectName = candidate.get[ObjectId]("project_id") match {
          case None => "Undefined"
          case Some(projectOid) => BWMongoDB3.projects.find(Map("_id" -> projectOid)).headOption match {
            case None => s"Not-Available ($projectOid)"
            case Some(project) => project.name[String]
          }
        }
        response.getWriter.println(s"INFO: CANDIDATE Name: '$documentName', Project: '$projectName'")
      }
    } else {
      response.getWriter.println("WARN: Found NO candidate documents")
    }
    candidateDocuments
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {

    val allActivities: Seq[DynDoc] = BWMongoDB3.activities.find()
    val allActivityOids: Seq[ObjectId] = allActivities.map(_._id[ObjectId])

    val projectOrphans = findProjectOrphans(response)
    response.getWriter.println("\n\n" + ("=" * 50))
    val activityOrphans = findActivityOrphans(allActivityOids, response)
    response.getWriter.println("\n\n" + ("=" * 50))
    val candidateRecords = findCandidateRecords(allActivityOids, response)
  }
}
