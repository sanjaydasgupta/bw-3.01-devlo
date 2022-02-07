package com.buildwhiz.tools

import java.io._

import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.sys.process._

@MultipartConfig()
class UploadFile extends HttpServlet with HttpUtils with DateTimeUtils {

  @tailrec
  private def copyStream(in: InputStream, out: FileOutputStream, length: Int = 0,
        buffer: Array[Byte] = new Array[Byte](1024)): Int = {
    in.read(buffer) match {
      case 0 | -1 => length
      case 1024 =>
        out.write(buffer)
        copyStream(in, out, length + buffer.length)
      case len =>
        out.write(buffer, 0, len)
        length + len
    }
  }

  private def archiveOldFiles(directory: File, fileName: String, tomcatDirName: String, request: HttpServletRequest): Unit = {
    val versionPattern = "[0-9a-z]{20}"
    val fileNamePattern = s"(.+)($versionPattern)(.+)".r
    val fileNameRegex = fileName match {
      case fileNamePattern(prefix, _, suffix) => s"$prefix$versionPattern$suffix"
      case _ => fileName
    }
    val files = directory.listFiles().filter(_.getName.matches(fileNameRegex)).sortBy(_.lastModified)
    if (files.length > 1) {
      val newestFile = files.last
      val previousFile = files.init.last
      val newestPath = newestFile.getAbsolutePath
      val previousPath = previousFile.getAbsolutePath
      val cmpStatus: Int = s"""cmp "$previousPath" "$newestPath" """.!
      val pw = new FileWriter("uploaded-files-diff.txt", true)
      val time = dateTimeString(System.currentTimeMillis)
      pw.write(s"\n$time - cmp(${previousFile.getName} ${newestFile.getName}) = $cmpStatus\n")
      pw.flush()
      pw.close()
      for (file <- files.init) {
        val path = file.getAbsolutePath
        val status = s"""mv "$path" server/$tomcatDirName/backups""".!
        if (status != 0) {
          BWLogger.log(getClass.getName, "purgeOldFiles()", s"ERROR: backup $status $path", request)
        }
      }
      BWLogger.audit(getClass.getName, "purgeOldFiles()", s"""Files-Purged: ${files.length - 1}""", request)
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost()", s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val rawRoles: Seq[String] = user.roles[Many[String]]
      if (!rawRoles.contains("BW-File-Uploads")) {
        throw new IllegalArgumentException("Not permitted")
      }
      val serverDir = new File("server")
      val tomcatDirName = serverDir.listFiles().find(_.getName.startsWith("apache-tomcat-")).get.getName
      val uploadsDirectory = new File("uploads")
      if (!uploadsDirectory.exists)
        throw new IllegalArgumentException("No 'uploads' directory")
      val backupsDirectory = new File("backups")
      if (!backupsDirectory.exists)
        throw new IllegalArgumentException("No 'backups' directory")
      if (parameters.contains("file_location")) {
        val fileLocation = parameters("file_location")
        //val locationParts = fileLocation.split("/")
        //val theFile = locationParts.foldLeft(uploadsDirectory)((dir, name) => new File(dir, name))
        //val fileOutputStream = new FileOutputStream(theFile)
        val directory = new File(uploadsDirectory, fileLocation)
        if (!directory.exists()) {
          val cwd = uploadsDirectory.getCanonicalPath
          throw new IllegalArgumentException(s"No directory '$fileLocation' in '$cwd'")
        }
        val parts = request.getParts.asScala
        if (parts.isEmpty)
          throw new IllegalArgumentException("No file uploaded")
        val fileName = parts.head.getSubmittedFileName
        val inputStream = parts.head.getInputStream
        val relativeFileName = fileLocation + "/" + fileName
        val fileOutputStream = new FileOutputStream(new File(uploadsDirectory, relativeFileName))
        val length = copyStream(inputStream, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()
        archiveOldFiles(directory, fileName, tomcatDirName, request)
        response.getWriter.println(s"Received $length bytes for '$relativeFileName' from ${request.getRemoteAddr}")
        response.setContentType("text/html")
        BWLogger.audit(getClass.getName, "doPost()", s"File-Loaded '$fileLocation/$fileName' ($length)", request)
      } else {
        val files = uploadsDirectory.listFiles.map(_.getPath)
        val status = s"""cp -fr ${files.mkString(" ")} server/$tomcatDirName/webapps/bw-3.01""".!
        val statusMsg = if (status == 0) {
          BWLogger.audit(getClass.getName, "doPost()", s"""File-Committed: ${files.mkString(", ")}""", request)
          "OK"
        } else {
          BWLogger.log(getClass.getName, "doPost()", s"""ERROR: Upload failure status: $status""", request)
          s"ERROR [$status]"
        }
        response.getWriter.println(s"""Update status: $statusMsg, files: ${files.mkString(", ")}""")
      }
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}