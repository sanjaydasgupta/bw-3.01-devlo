package com.buildwhiz.tools.scripts

import java.io.{File, FileOutputStream, PrintWriter}

import com.buildwhiz.baf2.{PersonApi, ProjectApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc, GoogleDrive, AmazonS3}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.HttpUtils
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

object MigrateAwsS3DataToGoogleDrive extends HttpUtils {

  private def selfTest(respWriter: PrintWriter): Unit = {
    respWriter.println("\nListing files in GoogleDrive storage folder")
    for (file <- GoogleDrive.listObjects()) {
      respWriter.println("\t%s (%s): %d".format(file.key, file.mimeType, file.size))
    }
    respWriter.flush()
    respWriter.println("\nFiles (txt, bat, sh) in local directory")
    val javaFiles = new File(".").listFiles(_.getName.matches(".+(?:txt|bat|sh)"))
    for (javaFile <- javaFiles) {
      respWriter.println("\t%s: %d".format(javaFile.getName, javaFile.length))
    }
    respWriter.flush()
    respWriter.println("\nLoading files into drive-storage")
    for (javaFile <- javaFiles) {
      try {
        val googleFile = GoogleDrive.putObject(javaFile.getName, javaFile)
        respWriter.println("\tOK: %s (%s): %d".format(googleFile.key, googleFile.mimeType, googleFile.size))
      } catch {
        case t: Throwable => respWriter.println("\t%s: %s: %d".format(t.toString, javaFile.getName, javaFile.length))
          //t.printStackTrace(respWriter)
      }
      respWriter.flush()
    }
    respWriter.println("\nDownloading files from GoogleDrive storage folder")
    for (googleFile <- GoogleDrive.listObjects().filter(_.key.startsWith("download-"))) {
      try {
        val outputStream = new FileOutputStream(googleFile.key)
        val inputStream = GoogleDrive.getObject(googleFile.key)
        val buffer = new Array[Byte](4096)
        var len = inputStream.read(buffer)
        while (len > 0) {
          outputStream.write(buffer, 0, len)
          len = inputStream.read(buffer)
        }
        outputStream.close()
        respWriter.println("\tOK: %s (%s): %d".format(googleFile.key, googleFile.mimeType, googleFile.size))
      } catch {
        case t: Throwable => respWriter.println("\t%s: %s: %d".format(t.toString, googleFile.key, googleFile.size))
        t.printStackTrace(respWriter)
      }
    }
    respWriter.flush()
  }

  private def migrateDate(projectId: String, respWriter: PrintWriter): Unit = {
    val projectOid = new ObjectId(projectId)
    if (!ProjectApi.exists(projectOid))
      throw new IllegalArgumentException(s"Bad projectId: '$projectOid'")
    respWriter.println(s"Migrating data for project '$projectId'")
    val projectDocuments: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("project_id" -> projectOid))
    val buffer = new Array[Byte](1048576)
    for (document <- projectDocuments if document.has("versions") && document.has("name")) {
      val versions: Seq[DynDoc] = document.versions[Many[Document]]
      respWriter.println(s"\tMigrating document '${document.name[String]}' (${versions.length} versions)")
      val documentOid = document._id[ObjectId]
      for (version <- versions) {
        val timestamp = version.timestamp[Long]
        val key = f"$projectId-$documentOid-$timestamp%x"
        respWriter.println(s"\t\tKey: $key")
        val tempFile = File.createTempFile(s"$key-", "bin")
        val outputStream = new FileOutputStream(tempFile)
        val inputStream = AmazonS3.getObject(key)
        var len = inputStream.read(buffer)
        while (len > 0) {
          outputStream.write(buffer, 0, len)
          len = inputStream.read(buffer)
        }
        outputStream.close()
        GoogleDrive.putObject(key, tempFile)
        tempFile.delete()
      }
    }
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val respWriter = response.getWriter
    respWriter.println("Starting Data Migration")

    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user))) {
      respWriter.println("Only Admins are permitted")
      throw new IllegalArgumentException("Not permitted")
    }

    try {
      args.length match {
        case 0 => selfTest(respWriter)
        case 1 => migrateDate(args(0), respWriter)
        case _ => respWriter.println("Program 'args' not understood!")
      }
    } catch {
      case t: Throwable =>
        respWriter.println(s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
