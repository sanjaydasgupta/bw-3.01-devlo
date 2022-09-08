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

object ConvertProjectTags extends HttpUtils {

  private def convertDocumentRecords(projectOid: ObjectId, output: String => Unit): Unit = {
    val projectDocs: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("project_id" -> projectOid))
    val allDocumentTags: Seq[String] = projectDocs.flatMap(d => {
      d.get[Many[String]]("labels") match {
        case Some(label) => label
        case None => Seq.empty[String]
      }
    }).map(_.trim).filter(_.nonEmpty).distinct
    val existingProjectTags: Seq[DynDoc] = BWMongoDB3.project_tags.find(Map("project_id" -> projectOid))
    val tags2Oids: Map[String, ObjectId] = existingProjectTags.map(t => (t.L1[String].trim, t._id[ObjectId])).
      filter(_._1.nonEmpty).toMap
    val badDocumentTags = allDocumentTags.filterNot(tags2Oids.contains)
    if (badDocumentTags.nonEmpty) {
      output(s"""<font color="red">MISSING tags: ${badDocumentTags.mkString(", ")}</font><br/>""")
    } else {
      output(s"""<font color="green">All document tags: ${allDocumentTags.mkString(", ")}</font><br/>""")
      for (doc <- projectDocs) {
        val docOid = doc._id[ObjectId]
        val docName = doc.name[String]
        val updateResult1 = BWMongoDB3.document_master.updateOne(Map("_id" -> docOid),
            Map($rename -> Map("labels" -> "labels2")))
        if (updateResult1.getModifiedCount == 1) {
          val existingLabelValues: Many[String] = doc.labels[Many[String]]
          val newLabelOids: Many[ObjectId] = existingLabelValues.map(_.trim).filter(_.nonEmpty).map(tags2Oids)
          val updateResult2 = BWMongoDB3.document_master.updateOne(Map("_id" -> docOid),
              Map($set -> Map("labels" -> newLabelOids)))
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

  private def convertProjectDocumentTags(projectOid: ObjectId, existingTags: Many[String], output: String => Unit): Unit = {
    val existingProjectTags: Seq[DynDoc] = BWMongoDB3.project_tags.find(Map("project_id" -> projectOid))
    val tags2Oids: Map[String, ObjectId] = existingProjectTags.map(t => (t.L1[String].trim, t._id[ObjectId])).
      filter(_._1.nonEmpty).toMap
    val badTags = existingTags.filterNot(tags2Oids.contains)
    if (badTags.nonEmpty) {
      output(s"""<font color="red">MISSING: ${badTags.mkString(", ")} not in collection 'project_tags'</font><br/>""")
    } else {
      val updateResult1 = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
          Map($rename -> Map("document_tags" -> "document_tags2")))
      if (updateResult1.getModifiedCount == 1) {
        val newDocumentTags: Many[ObjectId] = existingTags.map(tags2Oids)
        val updateResult2 = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
            Map($set -> Map("document_tags" -> newDocumentTags)))
        if (updateResult2.getModifiedCount == 1) {
          output(s"""<font color="green">ASSIGNED: 'document_tags' to array of OIDs</font><br/>""")
        } else {
          output(s"""<font color="red">FAILED to assign 'document_tags': $updateResult2</font><br/>""")
        }
      } else {
        output(s"""<font color="red">FAILED to rename 'document_tags' to 'document_tags2': $updateResult1</font><br/>""")
      }
    }
  }

  private def addProjectTagsEntries(projectOid: ObjectId, projectDocumentTags: Seq[String], output: String => Unit): Int = {
    val bulkWriteBuffer = projectDocumentTags.map(tn =>
        new InsertOneModel(new Document("__v", 0).append("L1", tn).append("project_id", projectOid)))
    output(s"Inserting ${bulkWriteBuffer.length} records into collection 'project_tags'<br/>")
    val bulkWriteResult = BWMongoDB3.project_tags.bulkWrite(bulkWriteBuffer.asJava)
    val insertCount = bulkWriteResult.getInsertedCount
    if (insertCount != bulkWriteBuffer.length) {
      output(s"""<font color="red">FAILED bulk-write to collection 'project_tags': $bulkWriteResult</font><br/>""")
    } else {
      output(s"""<font color="green">INSERTED $insertCount records into collection 'project_tags'</font><br/>""")
    }
    insertCount
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
            val projectRawDocumentTags: Seq[String] = theProject.document_tags[Many[Document]].map(_.name[String])
            val projectDocumentTags = projectRawDocumentTags.map(_.trim).filter(_.nonEmpty).distinct
            val documentTagsOk = projectRawDocumentTags.length == projectDocumentTags.length &&
                projectRawDocumentTags.zip(projectDocumentTags).forall(p => p._1 == p._2)
            if (documentTagsOk) {
              output(s"""Project 'document_tags': '${projectDocumentTags.mkString(", ")}'<br/>""")
              val existingProjectTags: Seq[DynDoc] = BWMongoDB3.project_tags.find(Map("project_id" -> projectOid))
              if (go) {
                if (existingProjectTags.nonEmpty) {
                  output(s"""<font color="red">ALREADY EXISTS: project_tags entries for this project</font><br/>""")
                } else {
                  val tagCount = addProjectTagsEntries(theProject._id[ObjectId], projectDocumentTags, output)
                  if (tagCount > 0) {
                    convertDocumentRecords(projectOid, output)
                    convertProjectDocumentTags(projectOid, projectDocumentTags, output)
                  }
                }
              }
            } else {
              output(s"""<font color="red">Project record's 'document_tags' CONTAINS SPACES</font><br/>""")
            }
        }
      } else {
        output(s"""<font color="red">Usage: ${getClass.getName} project-id [GO]</font><br/>""")
      }
      output(s"EXIT ${getClass.getName}:main()<br/>")
      output("</tt></body></html>")
    } catch {
      case t: Throwable =>
        writer.flush()
        t.printStackTrace(writer)
    }
    writer.flush()
  }

}
