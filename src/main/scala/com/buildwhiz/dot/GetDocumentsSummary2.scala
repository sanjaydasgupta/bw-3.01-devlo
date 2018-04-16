package com.buildwhiz.dot

//Get System defined labels
//Get custom defined labels
//Add Custom Labels
//Delete custom labels
//Assign labels to a document
//Remove the assignment

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.mutable

class GetDocumentsSummary2 extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getDocuments(user: DynDoc): Seq[Document] = {
    val count = user.first_name[String] match {
      case "Tester2" => 500
      case "Tester3" => 10
      case _ => 1
    }
    val userLabels: Seq[DynDoc] = user.labels[Many[Document]]
    val docOid2labels = mutable.Map.empty[ObjectId, mutable.Buffer[String]]
    for (label <- userLabels) {
      val labelName = label.name[String]
      val docOids: Seq[ObjectId] = label.document_ids[Many[ObjectId]]
      for (docOid <- docOids) {
        val docLabels: mutable.Buffer[String] = docOid2labels.getOrElse(docOid, mutable.Buffer.empty[String])
        docLabels.append(labelName)
        docOid2labels.put(docOid, docLabels)
      }
    }
    val docs: Seq[DynDoc] = BWMongoDB3.document_master.
        find(Map("category" -> Map("$exists" -> true), "category" -> Map("$ne" -> "SYSTEM"),
          "subcategory" -> Map("$exists" -> true), "versions.0.file_name" -> Map("$exists" -> true)))
    val docProperties: Seq[Document] = docs.map(d => {
      val lastVersion: DynDoc = d.versions[Many[Document]].head
      val fileType = lastVersion.file_name[String].split("\\.").last
      val date = dateTimeString(lastVersion.timestamp[Long], Some(user.tz[String]))
      val authorOid = lastVersion.author_person_id[ObjectId]
      val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
      val authorName = s"${author.first_name[String]} ${author.last_name[String]}"
      val systemLabels = Seq(d.category[String], d.subcategory[String])
      val userLabels = docOid2labels.getOrElse(d._id[ObjectId], Seq.empty[String])
      val prop: Document = Map("name" -> d.description[String], "_id" -> d._id[ObjectId].toString, "phase" -> "???",
        "labels" -> Map("system" -> systemLabels, "user" -> userLabels), "type" -> fileType,
        "author" -> authorName, "date" -> date)
      prop
    })
    docProperties.take(count)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val user: DynDoc = getUser(request)

      val allDocuments = getDocuments(user)
      writer.print(allDocuments.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${allDocuments.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}