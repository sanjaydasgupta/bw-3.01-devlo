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

  def deleteDocumentTag(personOid: ObjectId, tagName: String): Unit = {
    val userRecord: DynDoc = personById(personOid)
    val userTags: Seq[DynDoc] = documentTags(userRecord)
    if (!userTags.exists(_.name[String] == tagName))
      throw new IllegalArgumentException(s"Unknown tag '$tagName'")
    val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid),
      Map("$pull" -> Map("labels" -> Map("name" -> tagName))))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    BWMongoDB3.persons.updateOne(Map("_id" -> personOid,
      "document_filter_labels" -> Map("$exists" -> true)),
      Map("$pull" -> Map("document_filter_labels" -> tagName)))
  }

  def addDocumentTag(personOid: ObjectId, tagName: String, optLogic: Option[String]): Unit = {
    val userRecord: DynDoc = PersonApi.personById(personOid)
    val userTags: Seq[DynDoc] = documentTags(userRecord)
    if (userTags.exists(_.name[String] == tagName))
      throw new IllegalArgumentException(s"Tag '$tagName' already exists")
    val tagRecord = optLogic match {
      case None => Map("name" -> tagName, "document_ids" -> Seq.empty[ObjectId])
      case Some(logic) => Map("name" -> tagName, "document_ids" -> Seq.empty[ObjectId], "logic" -> logic)
    }
    val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid),
      Map("$push" -> Map("labels" -> tagRecord)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  def documentTags(person: DynDoc): Seq[DynDoc] = {
    if (person.has("labels")) {
      person.labels[Many[Document]]
    } else {
      Seq.empty[Document]
    }
  }

}
