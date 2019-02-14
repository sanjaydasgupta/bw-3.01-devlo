package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import org.bson.types.ObjectId
import org.bson.Document

object PersonApi {

  def personById(personOid: ObjectId): DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head

  def exists(personOid: ObjectId): Boolean = BWMongoDB3.persons.find(Map("_id" -> personOid)).nonEmpty

  def isBuildWhizAdmin(personOid: ObjectId): Boolean = {
    val userRecord = personById(personOid)
    userRecord.roles[Many[String]].contains("BW-Admin")
  }

  def documentTags(person: DynDoc): Seq[String] =
      if (person.has("labels")) person.labels[Many[Document]].map(_.name[String]) else Seq.empty[String]

}
