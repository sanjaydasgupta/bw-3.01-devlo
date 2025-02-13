package com.buildwhiz.api

import java.net.URI
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._
import scala.collection.mutable

trait RestUtils extends HttpUtils {

  def handleRestGet(request: HttpServletRequest, response: HttpServletResponse, apiName: String, collectionName: String,
      secretFields: Set[String] = Set.empty, sorter: Option[(Document, Document) => Boolean] = None,
      filter: Option[Document => Boolean] = None): Unit = {
    BWLogger.log(getClass.getName, "handleGet", s"ENTRY", request)
    try {
      val uriParts = new URI(request.getRequestURI.replace("[", "%5B").replace("]", "%5D")).getPath.split("/").toSeq.reverse
      val collection = BWMongoDB3(collectionName)
      val dynDocs: Seq[DynDoc] = uriParts match {
        case `apiName` +: _ =>
          val parameters = getParameterMap(request)
          collection.find(parametersToQuery(parameters))
        case id +: `apiName` +: _ =>
          collection.find(idToQuery(id))
      }
      val documents = dynDocs.map(_.asDoc)
      documents.foreach(d => secretFields.foreach(k => d.remove(k)))
      val sb = new StringBuilder("[")
      val sortedDocs = sorter match {
        case Some(s) => documents.sortWith(s)
        case None => documents
      }
      val filteredDocs = filter match {
        case Some(f) => sortedDocs.filter(f)
        case None => sortedDocs
      }
      for (p <- filteredDocs) {
        if (sb.length > 1)
          sb.append(",\n")
        sb.append(bson2json(p))
      }
      sb.append("]\n")
      response.getWriter.print(sb.toString())
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "handleGet", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "handleGet", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  def handleRestPut(request: HttpServletRequest, response: HttpServletResponse, apiName: String, collectionName: String): Unit = {
    BWLogger.log(getClass.getName, "handlePut", s"ENTRY", request)
    try {
      val uriParts = new URI(request.getRequestURI.replace("[", "%5B").replace("]", "%5D")).getPath.split("/").toSeq.reverse
      uriParts match {
        case id +: `apiName` +: _ =>
          val data = getStreamData(request)
          val entity = Document.parse(data)
          val updateResult = BWMongoDB3(collectionName).replaceOne(idToQuery(id), entity)
          response.getWriter.print(s"""{"MatchedCount": ${updateResult.getMatchedCount}, "ModifiedCount": ${updateResult.getModifiedCount}}\n""")
          response.setContentType("application/json")
          response.setStatus(HttpServletResponse.SC_OK)
          BWLogger.log(getClass.getName, "handlePut", s"EXIT-OK", request)
        case _ =>
          throw new NoSuchElementException("No resource id")
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "handlePut", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  def handleRestDelete(request: HttpServletRequest, response: HttpServletResponse, apiName: String, collectionName: String): Unit = {
    BWLogger.log(getClass.getName, "handleDelete", s"ENTRY", request)
    try {
      val uriParts = new URI(request.getRequestURI.replace("[", "%5B").replace("]", "%5D")).getPath.split("/").toSeq.reverse
      uriParts match {
        case id +: `apiName` +: _ =>
          val deleteResult = BWMongoDB3(collectionName).deleteMany(idToQuery(id))
          response.getWriter.print(s"""{"DeletedCount": ${deleteResult.getDeletedCount}}\n""")
          response.setContentType("application/json")
          response.setStatus(HttpServletResponse.SC_OK)
          BWLogger.log(getClass.getName, "handleDelete", s"EXIT-OK", request)
        case _ =>
          throw new NoSuchElementException("No resource id")
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "handleDelete", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  def handleRestPost(request: HttpServletRequest, response: HttpServletResponse, collectionName: String,
      dupChecker: Option[Document => Document] = None, updater: Option[Document => Document] = None): Document = {
    BWLogger.log(getClass.getName, "handlePost", s"ENTRY", request)
    try {
      val data = getStreamData(request)
      val inDocument = Document.parse(data)
      val checker = dupChecker match {
        case None => inDocument
        case Some(dc) => dc(inDocument)
      }
      val existingRecords = BWMongoDB3(collectionName).find(checker)
      if (existingRecords.nonEmpty)
        throw new IllegalArgumentException("Record already exists")
      val document = updater match {
        case Some(u) => u(inDocument)
        case None => inDocument
      }
      document.asScala("timestamps") = new Document("created", System.currentTimeMillis)
      BWMongoDB3(collectionName).insertOne(document)
      response.setContentType("text/plain")
      response.getWriter.print(s"""${request.getRequestURI}/${document.getObjectId("_id")}\n""")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "handlePost", s"EXIT-OK", request)
      document
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "handlePost", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  def userHasRole(request: HttpServletRequest, role: String): Boolean = {
    val user: DynDoc = getPersona(request)
    user.roles[Many[String]].contains(role)
  }

  def idToQuery(id: String): Document = {
    if (id.matches("^[0-9a-f]{24}$")) // MongoDB OID string
      new Document("_id", new ObjectId(id))
    else if (id.matches("^\\{.+\\}$")) // MongoDB query JSON
      Document.parse(id)
    else // MongoDB application-defined ID string
      new Document("_id", id)
  }

  def parametersToQuery(params: mutable.Map[String, String]): Document = {
    val query = new Document()
    for ((k, v) <- params) {
      if (k.endsWith("_id") && v.matches("^[0-9a-f]{24}$"))
        query.put(k, new ObjectId(v)) // stringified MongoDB id
      else
        query.put(k, v)
    }
    query
  }

}
