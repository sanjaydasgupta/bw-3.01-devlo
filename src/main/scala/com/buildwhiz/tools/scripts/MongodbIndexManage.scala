package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.HttpUtils
import org.bson.Document
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.annotation.unused
import scala.jdk.CollectionConverters._

@unused
object MongodbIndexManage extends HttpUtils {

  private val allIndexDefinitions: Map[String, Seq[Map[String, Int]]] = Map(
    "deliverables" -> Seq(Map("phase_id" -> 1, "activity_id" -> 1), Map("common_instance_no" -> 1), Map("deliverable_type" -> 1)),
    "deliverables_progress_infos" -> Seq(Map("person_id" -> 1), Map("activity_id" -> 1, "deliverable_id" -> 1))
  )

  private def manageIndices(go: Boolean, output: String => Unit): Unit = {
    output(s"${getClass.getName}:manageIndices(go=$go) ENTRY<br/>")
    val collectionNames = BWMongoDB3.collectionNames.sorted
    for (collectionName <- collectionNames) {
      val collection = BWMongoDB3(collectionName)
      val existingIndexDefs: Seq[Document] = collection.listIndexes().asScala.toSeq
      val existingIndexString = existingIndexDefs.map(_.toJson).mkString(", ")
      val existingIndexMessage = s"(${existingIndexDefs.length}) = $existingIndexString"
      output(s"""<br/><b>$collectionName</b>: Existing indexes $existingIndexMessage<br/>""")
      val existingIndexes: Seq[Map[String, Int]] = existingIndexDefs.map({idx =>
        val doc = idx.get("key").asInstanceOf[Document]
        doc.entrySet().asScala.toSeq.map(e => (e.getKey, e.getValue.asInstanceOf[Int])).toMap
      }).filterNot(m => m.size == 1 && m.contains("_id"))
      allIndexDefinitions.get(collectionName) match {
        case Some(requiredIndexes) =>
          val requiredIndexString = requiredIndexes.map(_.toJson).mkString(", ")
          val requiredIndexMessage = s"(${requiredIndexes.length}) = $requiredIndexString"
          output(s"""<b>$collectionName</b>: Required indexes $requiredIndexMessage<br/>""")
          val missingIndexes = requiredIndexes.filterNot(ri => existingIndexes.contains(ri))
          if (missingIndexes.nonEmpty) {
            val missingIndexesString = missingIndexes.map(m => new Document(m).toJson).mkString(", ")
            val missingIndexMessage = s"Missing indexes (${missingIndexes.length}) = $missingIndexesString"
            output(s"""<font color="red"><b>$collectionName</b>: $missingIndexMessage</font><br/>""")
            if (go) {
              for (missingIndex <- missingIndexes) {
                val indexName = collection.createIndex(new Document(missingIndex))
                val creationMessage = s"SUCCESS created index '$indexName' for ${new Document(missingIndex).toJson()}"
                output(s"""<font color="green"><b>$collectionName</b>: $creationMessage</font><br/>""")
              }
            }
          } else {
            output(s"""<font color="blue"><b>$collectionName</b>: No changes needed</font><br/>""")
          }
        case None =>
          output(s"""<font color="brown"><b>$collectionName</b>: Missing required index spec</font><br/>""")
      }
    }
    output(s"<br/>${getClass.getName}:manageIndices(go=$go) EXIT<br/><br/>")
  }

  @unused
  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    response.setContentType("text/html")
    output(s"<html><body>")
    output(s"${getClass.getName}:main() ENTRY<br/>")
    try {
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
        throw new IllegalArgumentException("Not permitted")
      }
      if (args.length <= 1) {
        val go: Boolean = args.length == 1 && args(0) == "GO"
        manageIndices(go, output)
        output(s"${getClass.getName}:main() EXIT-OK<br/>")
      } else {
        output(s"""<font color="red">${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} src-phase-id dest-phase-id [GO]</font><br/>""")
      }
    } catch {
      case t: Throwable =>
        output("%s(%s)<br/>".format(t.getClass.getName, t.getMessage))
        output(t.getStackTrace.map(_.toString).mkString("<br/>"))
    } finally {
      output("</body></html>")
    }
  }

}
