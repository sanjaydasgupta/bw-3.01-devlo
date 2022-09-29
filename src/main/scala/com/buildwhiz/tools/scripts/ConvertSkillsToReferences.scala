package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import org.bson.Document
import org.bson.types.ObjectId
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.UpdateOneModel

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object ConvertSkillsToReferences extends HttpUtils {

  private def convertSkills(skilledCollection: MongoCollection[Document], skill2id: Map[String, ObjectId],
      go: Boolean, output: String => Unit): Unit = {
    val collectionName = skilledCollection.getNamespace.getCollectionName
    output(s"${getClass.getName}:convertSkills($collectionName) ENTRY<br/>")
    val skilledRecords: Seq[DynDoc] = skilledCollection.find()
    output(s"Found ${skilledRecords.length} records in '$collectionName'<br/>")
    val skillsWithCode = skilledRecords.flatMap(_.skills[Many[String]].map(_.trim).filter(_.nonEmpty)).distinct
    val allSkills = skillsWithCode.map(skill => {
      val parts = skill.split(" [(][0-9]{2}-[0-9]{2} (BW|[0-9]{2}) (BW|[0-9]{2})")
      parts.head.trim
    })
    output(s"""Skill-List: ${allSkills.mkString(", ")}<br/>""")
    val unknownSkills = allSkills.filterNot(skill2id.contains)
    if (unknownSkills.nonEmpty) {
      output(s"""<font color="red">UNKNOWN skills (will EXIT): ${unknownSkills.mkString(", ")}</font><br/>""")
    } else {
      val skillsLabelContents = skilledRecords.flatMap(_.get[Many[String]]("skills_label") match {
        case Some(skills) => skills
        case None => Seq.empty[String]
      })
      if (skillsLabelContents.nonEmpty) {
        output(s"""<font color="red">EXISTS 'skills_label' (will EXIT): ${skillsLabelContents.mkString(", ")}</font><br/>""")
      } else if (go) {
        val renameSkillsResult = skilledCollection.updateMany(Map.empty[String, Any],
            Map($rename -> Map("skills" -> "skills_label")))
        if (renameSkillsResult.getModifiedCount != skilledRecords.length) {
          output(s"""<font color="red">FAILED rename 'skills' to 'skills_label' (will EXIT): $renameSkillsResult</font><br/>""")
        } else {
          val bulkSetSkillsList = skilledRecords.map(skilledRecord => {
            val oid = skilledRecord._id[ObjectId]
            val skills = skilledRecord.skills[Many[String]].map(_.trim).filter(_.nonEmpty)
            val skillOids: Many[ObjectId] = skills.map(skill => {
              val parts = skill.split(" [(][0-9]{2}-[0-9]{2} (BW|[0-9]{2}) (BW|[0-9]{2})")
              skill2id(parts.head.trim)
            })
            new UpdateOneModel[Document](new Document("_id", oid),
                new Document($set, new Document("skills", skillOids)))
          })
          val bulkSetResult = skilledCollection.bulkWrite(bulkSetSkillsList)
          if (bulkSetResult.getModifiedCount != bulkSetSkillsList.length) {
            output(s"""<font color="red">FAILED set 'skills': $bulkSetResult</font><br/>""")
          } else {
            output(s"""<font color="green">SUCCESS processing '$collectionName'</font><br/>""")
          }
        }
      }
    }
    output(s"${getClass.getName}:convertSkills($collectionName) EXIT<br/>")
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val go = args.length >= 1 && args(0) == "GO"
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    response.setContentType("text/html")
    output(s"<html><body>")

    output(s"${getClass.getName}:main() ENTRY<br/>")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    val omni33records: Seq[DynDoc] = BWMongoDB3.omni33classes.find()
    if (omni33records.isEmpty) {
      output(s"""<font color="red">NOT FOUND: Omniclass33 records - will QUIT</font><br/>""")
    } else {
      output(s"Omniclass33 Record count: ${omni33records.length}<br/>")
      val skill2id = omni33records.map(omni33 => (omni33.full_title[Many[String]].last, omni33._id[ObjectId])).toMap
      convertSkills(BWMongoDB3.persons, skill2id, go, output)
      convertSkills(BWMongoDB3.organizations, skill2id, go, output)
    }
    output(s"${getClass.getName}:main() EXIT-OK<br/>")
    output("</body></html>")
  }

}
