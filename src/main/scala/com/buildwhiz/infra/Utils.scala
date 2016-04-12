package com.buildwhiz.infra

import java.net.URI
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.io.Source

trait Utils {

  def bson2json(document: Document): String = {
    def obj2str(obj: Any): String = obj match {
      case s: String => s""""$s""""
      case oid: ObjectId => s""""${oid.toString}\""""
      case d: Document => bson2json(d)
      case seq: Seq[_] => seq.map(e => obj2str(e)).mkString("[", ", ", "]")
      //case ms: mutable.Seq[_] => ms.map(e => obj2str(e)).mkString("[", ", ", "]")
      case jList: ManyThings => jList.map(e => obj2str(e)).mkString("[", ", ", "]")
      case _ => obj.toString
    }
    document.toSeq.map(kv => s""""${kv._1}": ${obj2str(kv._2)}""").mkString("{", ", ", "}").replaceAll("\n", " ")
  }

  def getParameterMap(request: HttpServletRequest): mutable.Map[String, String] =
    request.getParameterMap.map(p => (p._1, p._2.mkString))

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

  def handleRestGet(request: HttpServletRequest, response: HttpServletResponse, apiName: String, collectionName: String,
                    secretFields: Set[String] = Set.empty, sorter: Option[(Document, Document) => Boolean] = None): Unit = {
    BWLogger.log(getClass.getName, "handleGet", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val uriParts = new URI(request.getRequestURI.replace("[", "%5B").replace("]", "%5D")).getPath.split("/").toSeq.reverse
      val collection = BWMongoDB3(collectionName)
      val documents: Seq[Document] = uriParts match {
        case `apiName` +: _ =>
          val parameters = getParameterMap(request)
          collection.find(parametersToQuery(parameters)).toSeq
        case id +: `apiName` +: _ =>
          collection.find(idToQuery(id)).toSeq
      }
      documents.foreach(d => secretFields.foreach(k => d.remove(k)))
      val sb = new StringBuilder("[")
      val sortedDocs = sorter match {
        case Some(s) => documents.sortWith(s)
        case None => documents
      }
      for (p <- sortedDocs) {
        if (sb.length > 1)
          sb.append(",\n")
        sb.append(bson2json(p))
      }
      sb.append("]\n")
      writer.print(sb.toString())
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "handleGet", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "handleGet", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
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
        t.printStackTrace()
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
        t.printStackTrace()
        throw t
    }
  }

  def handleRestPost(request: HttpServletRequest, response: HttpServletResponse, collectionName: String): Unit = {
    BWLogger.log(getClass.getName, "handlePost", s"ENTRY", request)
    try {
      val data = getStreamData(request)
      val document = Document.parse(data)
      document("timestamps") = new Document("created", System.currentTimeMillis)
      BWMongoDB3(collectionName).insertOne(document)
      response.setContentType("text/plain")
      response.getWriter.print(s"""${request.getRequestURI}/${document.getObjectId("_id")}\n""")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "handlePost", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "handlePost", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  def getStreamData(request: HttpServletRequest): String = {
    val source = Source.fromInputStream(request.getInputStream)
    source.getLines().mkString("\n")
  }

}
