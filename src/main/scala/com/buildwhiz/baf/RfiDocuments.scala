package com.buildwhiz.baf

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{AmazonS3, BWLogger, BWMongoDB3}
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.collection.mutable

class RfiDocuments extends HttpServlet with HttpUtils {

  private val rfiRequestOid = new ObjectId("56fe4e6bd5d8ad3da60d5d38")
  private val rfiResponseOid = new ObjectId("56fe4e6bd5d8ad3da60d5d39")
  private val rfiDocOids = Set(rfiRequestOid, rfiResponseOid)

  private def fillDocumentText(rfiDoc: DynDoc, projectOid: ObjectId, personOid: ObjectId): DynDoc = {
    val documentOid = rfiDoc.document_id[ObjectId]
    val timestamp = rfiDoc.timestamp[Long]
    val amazonS3Key = f"$projectOid-$documentOid-$timestamp%x"
    val inputStream: InputStream = AmazonS3.getObject(amazonS3Key).getObjectContent
    val byteBuffer = mutable.Buffer.empty[Byte]
    val blockBuffer = new Array[Byte](1024)
    def copyBuffer(): Unit = {
      val len = inputStream.read(blockBuffer)
      if (len > 0) {
        byteBuffer.append(blockBuffer.take(len): _*)
        copyBuffer()
      }
    }
    copyBuffer()
    val text: String = new String(byteBuffer.toArray).replaceAll("\"", "\\\\\"")
    rfiDoc.asDoc.put("text", text)
    rfiDoc.asDoc.put("type", if (documentOid == rfiRequestOid) "request" else "response")
    val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
    val timeZone = TimeZone.getTimeZone(person.tz[String])
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm z")
    sdf.setTimeZone(timeZone)
    val date = new Date(timestamp)
    val displayTime = sdf.format(date)
    rfiDoc.asDoc.put("time", displayTime)
    rfiDoc
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val activityOid = new ObjectId(parameters("activity_id"))
      val actionName = parameters("action_name")
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val rfiDocuments: Seq[DynDoc] = project.documents[DocumentList].filter(_.activity_id[ObjectId] == activityOid).
        filter(_.action_name[String] == actionName).filter(d => rfiDocOids.contains(d.document_id[ObjectId]))
      val personOid = new ObjectId(parameters("person_id"))
      val docsWithText = rfiDocuments.map(d => fillDocumentText(d, projectOid, personOid))
      writer.print(docsWithText.map(activity => bson2json(activity.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${docsWithText.length} objects)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}
