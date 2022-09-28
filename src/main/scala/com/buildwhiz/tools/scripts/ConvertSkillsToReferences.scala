package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import org.bson.Document
import org.bson.types.ObjectId
import com.mongodb.client.MongoCollection

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object ConvertSkillsToReferences extends HttpUtils {

  private def convertSkills(skilledCollection: MongoCollection[Document], skill2id: Map[String, ObjectId],
      go: Boolean, output: String => Unit): Unit = {
    val skilledRecords: Seq[DynDoc] = skilledCollection.find()
    output(s"Found ${skilledRecords.length} records in '${skilledCollection.getNamespace.getCollectionName}'<br/>")
    val skillsWithCode = skilledRecords.flatMap(_.skills[Many[String]].map(_.trim).filter(_.nonEmpty)).distinct
    val allSkills = skillsWithCode.map(skill => {
      val parts = skill.split(" [(][0-9]{2}-[0-9]{2} [0-9]{2} [0-9]{2}")
      parts.head.trim
    })
    val unknownSkills = allSkills.filterNot(skill2id.contains)
    if (unknownSkills.nonEmpty) {
      output(s"""<font color="red">Unknown skills: ${unknownSkills.mkString(", ")}</font><br/>""")
    } else {
      output(s"""Skill-List: ${allSkills.mkString(", ")}<br/>""")
    }
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
