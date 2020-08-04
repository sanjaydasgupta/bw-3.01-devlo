package com.buildwhiz.tools.scripts

import java.io.PrintWriter

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{DynDoc, GoogleDrive}
import com.buildwhiz.utils.HttpUtils
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object AddDocMetadataTrial extends HttpUtils {

  // https://developers.google.com/drive/api/v3/reference/files/update

  private def addMetadata(respWriter: PrintWriter): Unit = {
    respWriter.println("\nListing files in GoogleDrive storage folder")
    for (file <- GoogleDrive.listObjects()) {
      respWriter.println("\tId: %s, Key: %s, MimeType: %s: Size: %d".format(file.id, file.key, file.mimeType, file.size))
      GoogleDrive.updateObject(file.key, Map("type" -> file.mimeType, "length" -> file.size.toString))
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
