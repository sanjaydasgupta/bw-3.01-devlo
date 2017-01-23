package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{DateTimeUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class RFIMessagesFetch extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def limitText(t: String) = if (t.length < 70) t else t.substring(0, 70) + " ..."

  private def memberNames(memberOids: ObjectIdList): Seq[String] = {
    memberOids.asScala.map(oid => {
      val member: DynDoc = BWMongoDB3.persons.find(Map("_id" -> oid)).asScala.head
      s"${member.first_name[String]} ${member.last_name[String]}"
    })
  }

  private def documentInfo(rfiDocRef: DynDoc): Map[String, String] = {
    val documentOid = rfiDocRef.document_id[ObjectId]
    val documentTimestamp = rfiDocRef.version[Long]
    val theDocument: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).asScala.head
    val docCategory = theDocument.category[String]
    val docSubcategory = theDocument.subcategory[String]
    val docName = theDocument.name[String]
    val docDescription = theDocument.description[String]
    val documentVersions: Seq[DynDoc] = theDocument.versions[DocumentList]
    val theVersion: DynDoc = documentVersions.filter(_.timestamp[Long] == documentTimestamp).head
    val versionComments = theVersion.comments[String]
    val fileName = if (theVersion.has("file_name")) theVersion.file_name[String] else docName
    val downloadLink = s"""baf/DocumentVersionDownload/$fileName?document_master_id=$documentOid&timestamp=$documentTimestamp"""
    Map("category" -> docCategory, "subcategory" -> docSubcategory, "name" -> docName, "description" -> docDescription,
        "comments" -> versionComments, "link" -> downloadLink, "file_name" -> fileName)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val query = (parameters.get("person_id"), parameters.get("rfi_ids")) match {
        case (None, Some(rfiIds)) =>
          Map("project_id" -> project430ForestOid,
            "_id" -> Map("$in" -> rfiIds.split(",").map(id => new ObjectId(id.trim)).toSeq))
        case (Some(personId), None) =>
          Map("project_id" -> project430ForestOid, "members" -> new ObjectId(personId))
        case _ => Map.empty[String, AnyRef]
      }
      val rfiExchanges: Seq[DynDoc] = BWMongoDB3.rfi_messages.find(query).asScala.toSeq
      val rfiLines: Seq[Document] = rfiExchanges.map(rfi => {
        val messages: Seq[DynDoc] = rfi.messages[DocumentList]
        val lastMessage = messages.last
        val sender: DynDoc = BWMongoDB3.persons.find(Map("_id" -> lastMessage.sender[ObjectId])).asScala.head
        val senderName = s"${sender.first_name[String]} ${sender.last_name[String]}"
        val clientTimezone = getUser(request).get("tz").asInstanceOf[String]
        // "document_info" -> new Document(docInfo)
        val rfiInfo = new Document(Map("_id" -> rfi._id[ObjectId], "subject" -> rfi.subject[String],
          "member_names" -> memberNames(rfi.members[ObjectIdList]), "count" -> messages.length,
          "timestamp" -> dateTimeString(lastMessage.timestamp[Long], Some(clientTimezone)),
          "last_message" -> limitText(lastMessage.text[String]), "sender" -> senderName))
        val docReference: DynDoc = rfi.document[Document]
        rfiInfo.put("document_info", new Document(documentInfo(docReference)))
        rfiInfo
      })
      response.getWriter.print(rfiLines.map(bson2json).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
