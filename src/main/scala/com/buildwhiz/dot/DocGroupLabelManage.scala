package com.buildwhiz.dot

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocGroupLabelManage extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    //val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val usersLabelRecords: Seq[DynDoc] =
          if (person.has("labels")) person.labels[Many[Document]] else Seq.empty[Document]
      val labelDataStream = getStreamData(request)
      val labelData: DynDoc = if (labelDataStream.nonEmpty) Document.parse(labelDataStream) else new Document()
      val docOids: Seq[ObjectId] = labelData.docIds[Many[String]].map(id => new ObjectId(id))
      val labels: Seq[String] = labelData.labels[Many[String]]
      val op = labelData.op[String]
      val labelIndices = labels.map(label => usersLabelRecords.indexWhere(_.name[String] == label))
      val (modifiedCount, opName) = op match {
        case "add" =>
          val existingLabelRecords = labelIndices.filter(_ != -1).map(idx => usersLabelRecords(idx))
          val logicLabelNames = existingLabelRecords.
              filter(rec => rec.has("logic") && rec.logic[String].trim.nonEmpty).map(_.name[String])
          if (logicLabelNames.nonEmpty)
            throw new IllegalArgumentException(s"""Labels already have logic: ${logicLabelNames.mkString(", ")}""")
          val updateResults = docOids.flatMap(docOid =>
            labelIndices.zip(labels).map(idx =>
              if (idx._1 == -1)
                BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
                  Map("$addToSet" -> Map("labels" -> Map("name" -> idx._2, "document_ids" -> Seq(docOid)))))
              else
                BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
                  Map("$addToSet" -> Map(s"labels.${idx._1}.document_ids" -> docOid)))
            )
          )
          (updateResults.map(_.getModifiedCount).sum, "Added")
        case "remove" =>
          val updateResults = docOids.flatMap(docOid =>
            labelIndices.filter(_ != -1).map(labelIndex => BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
            Map("$pull" -> Map(s"labels.$labelIndex.document_ids" -> docOid)))))
          (updateResults.map(_.getModifiedCount).sum, "Removed")
        case unknown => throw new IllegalArgumentException(s"operation '$unknown' not recognized")
      }
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, request.getMethod,
        s"""$opName document '${docOids.mkString(",")}' to label '${labels.mkString(",")}' [$modifiedCount Ok]""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
