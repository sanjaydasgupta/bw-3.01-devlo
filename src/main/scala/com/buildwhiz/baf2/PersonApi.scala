package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

object PersonApi {

  val possibleIndividualRoles: Seq[String] = Seq("Principal", "Admin", "Finance", "Contract", "Lead", "Contributor")

  def personsByIds(personOids: Seq[ObjectId]): Seq[DynDoc] =
    BWMongoDB3.persons.find(Map("_id" -> Map($in -> personOids)))

  def personById(personOid: ObjectId): DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head

  def exists(personOid: ObjectId): Boolean = BWMongoDB3.persons.find(Map("_id" -> personOid)).nonEmpty

  def isBuildWhizAdmin(who: Either[ObjectId, DynDoc]): Boolean = {
    val userRecord = who match {
      case Right(dynDoc) => dynDoc
      case Left(oid) => personById(oid)
    }
    userRecord.roles[Many[String]].contains("BW-Admin")
  }

  def inSameOrganization(person1Oid: ObjectId, person2Oid: ObjectId): Boolean = {
    val person1 = personById(person1Oid)
    val person2 = personById(person2Oid)
    person1.has("organization_id") && person2.has("organization_id") &&
        person1.organization_id[ObjectId] == person2.organization_id[ObjectId]
  }

  def fetch(optWorkEmail: Option[String] = None, optOrganizationOid: Option[ObjectId] = None, optSkill: Option[String] = None): Seq[DynDoc] = {
    (optWorkEmail, optOrganizationOid, optSkill) match {
      case (Some(workEmail), _, _) =>
        BWMongoDB3.persons.find(Map("emails" -> Map("$elemMatch" ->
          Map("$eq" -> Map("type" -> "work", "email" -> workEmail)))))
      case (None, Some(organizationOid), Some(skill)) =>
        BWMongoDB3.persons.find(Map("organization_id" -> organizationOid, "skills" -> skill))
      case (None, Some(organizationOid), None) =>
        BWMongoDB3.persons.find(Map("organization_id" -> organizationOid))
      case (None, None, Some(skill)) => BWMongoDB3.persons.find(Map("skills" -> skill))
      case _ => BWMongoDB3.persons.find()
    }
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

  def fullName(person: DynDoc): String = s"${person.first_name[String]} ${person.last_name[String]}"

  def documentTags(person: DynDoc): Seq[DynDoc] = {
    if (person.has("labels")) {
      person.labels[Many[Document]]
    } else {
      Seq.empty[Document]
    }
  }

  def documentGroupManageLabels(person: DynDoc, documentOids: Seq[ObjectId], tagNames: Seq[String],
      operation: String): Unit = {
    val allUserTags = documentTags(person)
    val badTagNames = tagNames.filter(tagName => !allUserTags.exists(_.name[String] == tagName))
    if (badTagNames.nonEmpty)
      throw new IllegalArgumentException(s"""Bad tag-names: ${badTagNames.mkString(", ")}""")

    val badDocumentOids = documentOids.filter(oid => !DocumentApi.exists(oid))
    if (badDocumentOids.nonEmpty)
      throw new IllegalArgumentException(s"""Bad document-ids: ${badDocumentOids.mkString(", ")}""")

    if (operation == "add") {
      tagNames.map(tagName => person.labels[Many[Document]].indexWhere(_.name[String] == tagName)).foreach(idx => {
        val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> person._id[ObjectId]),
          Map("$addToSet" -> Map(s"labels.$idx.document_ids" -> Map("$each" -> documentOids))))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      })
    } else if (operation == "remove") {
      tagNames.map(tagName => person.labels[Many[Document]].indexWhere(_.name[String] == tagName)).foreach(idx => {
        val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> person._id[ObjectId]),
          Map("$pullAll" -> Map(s"labels.$idx.document_ids" -> documentOids)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      })
    } else
      throw new IllegalArgumentException(s"Bad operation: '$operation'")
  }

  def person2document(person: DynDoc): Document = {
    val name = fullName(person)
    val active = if (person.has("enabled")) person.enabled[Boolean] else false
    val individualRoles: java.util.Collection[String] = if (person.has("individual_roles"))
      person.individual_roles[Many[String]]
    else
      Seq.empty[String].asJava

    new Document("_id", person._id[ObjectId]).append("name", name).append("skills", person.skills[Many[String]]).
        append("years_experience", person.years_experience[Int]).append("rating", person.rating[Double]).
        append("active", active).append("individual_roles", individualRoles)
  }

  def parentOrganization(personIn: Either[ObjectId, DynDoc]): Option[DynDoc] = {
    val person = personIn match {
      case Right(pd) => pd
      case Left(pOid) => personById(pOid)
    }
    if (person.has("organization_id")) {
      val organization = OrganizationApi.organizationById(person.organization_id[ObjectId])
      Some(organization)
    } else {
      None
    }
  }

  def vCard(personIn: Either[ObjectId, DynDoc]): String = {
    val person = personIn match {
      case Right(pd) => pd
      case Left(pOid) => personById(pOid)
    }
    val (firstName, lastName) = (person.first_name[String], person.last_name[String])
    val n = s"N:$lastName;$firstName;;;"
    val fn = s"FN:$firstName $lastName"
    val org = parentOrganization(Right(person)) match {
      case Some(orgRecord) => s"ORG:${orgRecord.name[String]}"
      case None => ""
    }
    val workEmailRecord: DynDoc = person.emails[Many[Document]].find(_.`type`[String] == "work").get
    val workEmail = s"EMAIL:${workEmailRecord.email[String]}"
    val workPhoneRecord: Option[DynDoc] = person.phones[Many[Document]].find(_.`type`[String] == "work")
    val workPhone = workPhoneRecord match {
      case None => ""
      case Some(wp) => s"TEL;TYPE=WORK,VOICE:${wp.phone[String]}"
    }
    val parts = Seq("BEGIN:VCARD", "VERSION:3.0", n, fn, org, workEmail, workPhone, "END:VCARD")
    parts.filter(_.nonEmpty).mkString("\n")
  }

  def validateNewName(newName: String): Boolean = {
    val nameLength = newName.length
    if (newName.trim.length != nameLength)
      throw new IllegalArgumentException(s"Bad name (has blank padding): '$newName'")
    if (nameLength > 30 || nameLength == 0)
      throw new IllegalArgumentException(s"Bad name length: $nameLength")
    true
  }

}
