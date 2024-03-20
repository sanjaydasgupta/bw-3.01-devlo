package com.buildwhiz.tools.scripts

import java.io.PrintWriter

import com.buildwhiz.baf2.{DocumentApi, PersonApi, PhaseApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc, GoogleDriveRepository}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document
import com.buildwhiz.infra.FileMetadata

object AddDocMetadataTrial extends HttpUtils with DateTimeUtils {

  // https://developers.google.com/drive/api/v3/reference/files/update

  val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
  val projectIdNameMap: Map[ObjectId, String] =
      projects.map(project => (project._id[ObjectId], project.name[String])).toMap

  val phases: Seq[DynDoc] = BWMongoDB3.phases.find()
  val phaseIdNameMap: Map[ObjectId, String] =
    phases.map(phase => (phase._id[ObjectId], phase.name[String])).toMap

  val processes: Seq[DynDoc] = BWMongoDB3.processes.find()
  val processIdNameMap: Map[ObjectId, String] =
    processes.map(process => (process._id[ObjectId], process.name[String])).toMap

  private def fileIdAndProperties(projectOid: ObjectId): Seq[(String, Map[String, String])] = {
    def checkPhase(docRecord: DynDoc): Boolean = {
      docRecord.get[ObjectId]("phase_id") match {
        case None => true
        case Some(phaseOid) => phaseIdNameMap.contains(phaseOid)
      }
    }
    def checkProcess(docRecord: DynDoc): Boolean = {
      docRecord.get[ObjectId]("process_id") match {
        case None => true
        case Some(processOid) => processIdNameMap.contains(processOid)
      }
    }
    val files: Seq[FileMetadata] = GoogleDriveRepository.listObjects(Some(projectOid.toString))
    files.map(file => {
      val Array(_, documentId, timestampHex) = file.key.split("-")
      val timestamp = java.lang.Long.parseLong(timestampHex, 16)
      val documentOid = new ObjectId(documentId)
      if (DocumentApi.exists(documentOid)) {
        (file, Some(documentOid), timestamp)
      } else {
        (file, None, timestamp)
      }
    }).filter(_._2.nonEmpty).map(fd => (fd._1, DocumentApi.documentById(fd._2.get), fd._3)).
        filter(t => checkPhase(t._2)).filter(t => checkProcess(t._2)).map(t => {
      val fileId: String = t._1.id
      val fileName = t._2.name[String]
      val timestamp: Long = t._3
      val tags: Seq[String] = if (t._2.has("labels")) t._2.labels[Many[String]] else Seq.empty[String]
      val versions: Seq[DynDoc] = t._2.versions[Many[Document]]
      val phaseName = t._2.get[ObjectId]("phase_id") match {
        case None => null
        case Some(phaseOid) => PhaseApi.phaseById(phaseOid).name[String]
      }
      val authorName = versions.find(_.timestamp[Long] == timestamp) match {
        case None => null
        case Some(version) => PersonApi.fullName(PersonApi.personById(version.author_person_id[ObjectId]))
      }
      (fileId, Map("project" -> projectIdNameMap(projectOid), "phase" -> phaseName, "name" -> fileName,
        "timestamp" -> dateTimeString(timestamp), "author" -> authorName, "tags" -> tags.mkString(",")))
    })
  }

  private def addMetadata(respWriter: PrintWriter): Unit = {
    respWriter.println("Listing files in GoogleDrive storage folder")
    for (projectOid <- projectIdNameMap.keys) {
      respWriter.println(s"Getting file-metadata for project $projectOid")
      val fileIdsAndMetadatas = fileIdAndProperties(projectOid)
      for ((fid, properties) <- fileIdsAndMetadatas) {
        respWriter.println(s"fileId: $fid, properties: $properties")
        GoogleDriveRepository.updateObjectById(fid, properties)
      }
    }
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val respWriter = response.getWriter
    respWriter.println("Adding Metadata")

    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user))) {
      respWriter.println("Only Admins are permitted")
      throw new IllegalArgumentException("Not permitted")
    }

    try {
      args.length match {
        case 0 => addMetadata(respWriter)
        //case 1 => migrateDate(args(0), respWriter)
        case _ => respWriter.println("Program 'args' not understood!")
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace(respWriter)
        //respWriter.println(s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
