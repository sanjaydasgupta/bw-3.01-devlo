package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import com.mongodb.client.model.InsertOneModel
import org.bson.types.ObjectId
import org.bson.Document

import scala.jdk.CollectionConverters._

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.collection.mutable

object CreateProjectTagsCollection extends HttpUtils {

  private def convertDocumentRecords(projectOid: ObjectId, projectTagNames: Seq[String], output: String => Unit): Unit = {
    val projectDocs: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("project_id" -> projectOid))
    val allDocumentTags: Seq[String] = projectDocs.flatMap(d => {
      d.get[Many[String]]("labels") match {
        case Some(label) => label
        case None => Seq.empty[String]
      }
    }).map(_.trim).filter(_.nonEmpty).distinct
    val existingProjectTags: Seq[DynDoc] = BWMongoDB3.project_tags.find(Map("project_id" -> projectOid))
    val tags2Oids: Map[String, ObjectId] = existingProjectTags.map(t => (t.L1[String], t._id[ObjectId])).toMap
    val badDocumentTags = allDocumentTags.filterNot(tags2Oids.contains)
    if (badDocumentTags.nonEmpty) {
      output(s"""<font color="red">MISSING tags: ${badDocumentTags.mkString(", ")}<font/><br/>""")
    } else {
      output(s"""<font color="green">All document tags: ${allDocumentTags.mkString(", ")}</font><br/>""")
      for (doc <- projectDocs) {
        val docOid = doc._id[ObjectId]
        val docName = doc.name[String]
        val updateResult1 = BWMongoDB3.document_master.updateOne(Map("_id" -> docOid),
            Map($rename -> Map("labels" -> "labels2")))
        if (updateResult1.getModifiedCount == 1) {
          val labelOids: Many[ObjectId] = doc.labels[Many[String]].map(tags2Oids)
          val updateResult2 = BWMongoDB3.document_master.updateOne(Map("_id" -> docOid),
            Map($set -> Map("labels" -> labelOids)))
          if (updateResult2.getModifiedCount == 1) {
            output(s"""<font color="green">SUCCESSFULLY Relabeled: $docName ($docOid)</font><br/>""")
          } else {
            output(s"""<font color="red">FAILED to add label: $docName ($docOid) error: $updateResult2 skipping</font><br/>""")
          }
        } else {
          output(s"""<font color="red">FAILED to rename label to label2: $docName ($docOid) error: $updateResult1 skipping</font><br/>""")
        }
      }
    }
  }

  private def createNewTags(projectOid: ObjectId, projectTagNames: Seq[String], output: String => Unit): Int = {
    val bulkWriteBuffer = mutable.Buffer[InsertOneModel[Document]]()
    for (tagName <- projectTagNames) {
      val newRecord = new Document("__v", 0).append("L1", tagName).append("project_id", projectOid)
      bulkWriteBuffer.append(new InsertOneModel(newRecord))
    }
    output(s"Inserting ${bulkWriteBuffer.length} records into collection 'project_tags'<br/>")
    val bulkWriteResult = BWMongoDB3.project_tags.bulkWrite(bulkWriteBuffer.asJava)
    if (bulkWriteResult.getInsertedCount != bulkWriteBuffer.length) {
      output(s"""<font color="red">ERROR: $bulkWriteResult<font/><br/>""")
    } else {
      output(s"Inserted ${bulkWriteResult.getInsertedCount} records<br/>")
    }
    bulkWriteResult.getInsertedCount
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)

    try {
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
        throw new IllegalArgumentException("Not permitted")
      }
      response.setContentType("text/html")

      output("<html><body><tt>")
      output(s"ENTRY ${getClass.getName}:main()<br/>")
      if (args.length >= 1) {
        val go: Boolean = args.length == 2 && args(1) == "GO"
        val projectOid = new ObjectId(args(0))
        BWMongoDB3.projects.find(Map("_id" -> projectOid)).headOption match {
          case None =>
            output(s"""<font color="red">No such project ID: '${args(0)}'</font><br/>""")
          case Some(theProject) =>
            output(s"Found project: '${theProject.name[String]}'<br/>")
            val projectTagNames: Seq[String] = theProject.document_tags[Many[Document]].map(_.name[String].trim).
              filter(_.nonEmpty).distinct
            output(s"""Found tags: '${projectTagNames.mkString(", ")}'<br/>""")
            val existingTagCount = BWMongoDB3.project_tags.countDocuments(Map("project_id" -> projectOid))
            if (go) {
              if (existingTagCount > 0) {
                output(s"""<font color="red">EXITING: project_tags exists (with $existingTagCount records)</font><br/>""")
              } else {
                val tagCount = createNewTags(theProject._id[ObjectId], projectTagNames, output)
                if (tagCount > 0) {
                  convertDocumentRecords(projectOid, projectTagNames, output)
                }
              }
            }
        }
      } else {
        output(s"""<font color="red">Usage: ${getClass.getName} project-id [GO]</font><br/>""")
      }
      output(s"EXIT ${getClass.getName}:main()<br/>")
      output("</tt></body></html>")
    } catch {
      case t: Throwable => t.printStackTrace(writer)
    }
    writer.flush()
  }

}
