package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{PersonApi, ProjectApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentCreate extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      type CONVERTER = String => Any
      val checkCategory: CONVERTER =
          cat => if (masterData("Docs__category").asInstanceOf[Seq[String]].contains(cat))
            cat
          else
            throw new IllegalArgumentException(s"bad category '$cat'")
      val noOp: CONVERTER = _.trim
      val oid: CONVERTER = new ObjectId(_)
      val toArray: CONVERTER = _.split(",").map(_.trim).toSeq
      val nameValuePairs: Map[String, Any] = Array(
          ("deliverable_id", oid, None), ("activity_id", oid, None), ("phase_id", oid, None),
          ("project_id", oid, None), ("description", noOp, None), ("file_format", noOp, Some("type")),
          ("team_id", oid, None), ("name", noOp, None), ("tags", toArray, Some("labels")),
          ("category", checkCategory, None)).flatMap(triple => parameters.get(triple._1).
          map(value => (triple._3 match {case None => triple._1; case Some(name) => name}, triple._2(value)))).toMap

      val missingParams = Seq("project_id", "phase_id", "team_id", "category", "name", "type").
          filterNot(nameValuePairs.contains)
      if (missingParams.nonEmpty)
        throw new IllegalArgumentException(s"""Missing parameters: ${missingParams.mkString(", ")}""")

      val projectOid = nameValuePairs.get("project_id") match {
        case Some(pOid) => pOid.asInstanceOf[ObjectId]
        case None =>
          throw new IllegalArgumentException("project_id not provided")
      }

      val user: DynDoc = getPersona(request)
      if (!ProjectApi.canManage(user._id[ObjectId], ProjectApi.projectById(projectOid)) &&
          !PersonApi.isBuildWhizAdmin(Right(user)))
        throw new IllegalArgumentException("Not permitted")

      val documentRecord = (nameValuePairs ++ Map("versions" -> Seq.empty[Document])).asDoc
      val disposition: String = if (nameValuePairs.contains("deliverable_id") &&
          nameValuePairs.getOrElse("category", "") == "Specification") {
        val query = Map("deliverable_id" -> nameValuePairs("deliverable_id"), "category" -> "Specification")
        BWMongoDB3.document_master.find(query).headOption match {
          case None =>
            BWMongoDB3.document_master.insertOne(documentRecord)
            "Created"
          case Some(existingRecord) =>
            documentRecord.append("_id", existingRecord._id[ObjectId])
            "Found"
        }
      } else {
        BWMongoDB3.document_master.insertOne(documentRecord)
        "Created"
      }

      val docOid = documentRecord.getObjectId("_id").toString
      response.getWriter.print(successJson(fields = Map("document_id" -> docOid)))
      response.setContentType("application/json")
      val message = s"""$disposition document name='${nameValuePairs("name")}', _id='$docOid'"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}