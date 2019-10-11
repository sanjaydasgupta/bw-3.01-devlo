package com.buildwhiz.baf2

import java.io.OutputStream
import java.nio.file.attribute.FileTime
import java.util.zip.{ZipEntry, ZipOutputStream}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class OrganizationVcard extends HttpServlet with HttpUtils {

  private def zipVCards(vCards: Seq[String], outStream: OutputStream, request: HttpServletRequest): Unit = {
    val zipOutputStream = new ZipOutputStream(outStream)
    val ms = System.currentTimeMillis()
    for (vCard <- vCards) {
      val name = vCard.split("\n").find(_.startsWith("FN:")).get.substring(3)
      val zipEntry = new ZipEntry(name)
      zipEntry.setCreationTime(FileTime.fromMillis(ms))
      //zipEntry.setLastModifiedTime(FileTime.fromMillis(timestamp))
      zipOutputStream.putNextEntry(zipEntry)
      zipOutputStream.write(vCard.getBytes)
      zipOutputStream.closeEntry()
    }
    zipOutputStream.close()
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val organizationOid = new ObjectId(parameters("organization_id"))
      val organization = OrganizationApi.organizationById(organizationOid)
      val fileName = organization.name[String].toList.map({
        case '\\' => '|'
        case '/' => '|'
        case ' ' => '-'
        case c => c
      }).mkString
      val persons: Seq[DynDoc] = BWMongoDB3.persons.find(Map("organization_id" -> organizationOid))
      val vCards = persons.map(person => PersonApi.vCard(Right(person)))
      val outputStream = response.getOutputStream
      response.setContentType("application/zip")
      response.setHeader("Content-Disposition", s"attachment; filename=$fileName.zip")
      zipVCards(vCards, outputStream, request)
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
