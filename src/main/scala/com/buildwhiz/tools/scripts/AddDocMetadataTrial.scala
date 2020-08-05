package com.buildwhiz.tools.scripts

import java.io.PrintWriter

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc, GoogleDrive}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.HttpUtils
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import com.buildwhiz.infra.FileMetadata

object AddDocMetadataTrial extends HttpUtils {

  // https://developers.google.com/drive/api/v3/reference/files/update

  val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
  val projectIdNameMap: Map[ObjectId, String] =
      projects.map(project => (project._id[ObjectId], project.name[String])).toMap
  val projectOids: Seq[ObjectId] = projects.map(_._id[ObjectId])

  private def addMetadata(respWriter: PrintWriter): Unit = {
    respWriter.println("\nListing files in GoogleDrive storage folder")
    val files: Seq[FileMetadata] = GoogleDrive.listObjects().
        filter(f => projectIdNameMap.contains(new ObjectId(f.key.split("-").head)))
    for (file <- files) {
      val key = file.key
      val Array(projectId, documentId, _) = key.split("-")
      val document: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> new ObjectId(documentId))).head
      val labels: Seq[String] = if (document.has("labels")) document.labels[Many[String]] else Seq.empty
      val projectName = projectIdNameMap(new ObjectId(projectId))
      val metadata = Map("project" -> projectName, "tags" -> labels.mkString(","))
      val documentName = if (document.has("name")) document.name[String] else "none"
      respWriter.println(s"Assigning document '$documentName' ($documentId) metadata = $metadata")
      GoogleDrive.updateObject(key, metadata)
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
        //BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
