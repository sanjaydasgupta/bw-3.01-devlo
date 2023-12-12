package com.buildwhiz.baf3

import com.buildwhiz.baf2.{DocumentApi, PersonApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import java.util.regex.Pattern
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentLink extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val t0 = System.currentTimeMillis()
      val documentOid = new ObjectId(parameters("document_id"))
      if (!DocumentApi.exists(documentOid))
        throw new IllegalArgumentException(s"unknown document-id: $documentOid")

      val user: DynDoc = getPersona(request)
      val authorOid = parameters.get("author_id") match {
        case Some(aId) =>
          if (!PersonApi.isBuildWhizAdmin(Right(user)))
            throw new IllegalArgumentException("Not permitted to provide author_id")
          new ObjectId(aId)
        case None => user._id[ObjectId]
      }
      if (!PersonApi.exists(authorOid))
        throw new IllegalArgumentException(s"unknown author-id: ${authorOid.toString}")

      val linkUrl = parameters("link_url")

      val optLinkFileName = {
        val regex = Pattern.compile("(?i)(?:[^/]*/+)+(.+[.][a-z]{2,4})")
        val matcher = regex.matcher(linkUrl)
        if (matcher.matches()) {
          Some(matcher.group(1))
        } else {
          None
        }
      }
      val documentRecord: DynDoc = DocumentApi.documentById(documentOid)
      val fileName = (parameters.get("file_name"), optLinkFileName) match {
        case (Some(inputFileName), _) => inputFileName
        case (None, Some(linkFileName)) => linkFileName
        case _ => s"""${documentRecord.name[String].replaceAll("\\s+", "-")}.${documentRecord.`type`[String]}"""
      }
      val timestamp = System.currentTimeMillis

      val versionComments = if (parameters.contains("comments")) parameters("comments") else "-"

      val versionRecord = Map("comments" -> versionComments, "timestamp" -> timestamp, "author_person_id" -> authorOid,
        "file_name" -> fileName, "size" -> -1, "link_url" -> linkUrl)
      val updateResult = BWMongoDB3.document_master.
        updateOne(Map("_id" -> documentOid), Map("$push" -> Map("versions" -> versionRecord)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      response.setStatus(HttpServletResponse.SC_OK)
      val delay = System.currentTimeMillis() - t0
      val message = s"time: $delay ms, Linked version to file ${documentRecord.name[String]}"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
      response.getWriter.print(successJson())
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
