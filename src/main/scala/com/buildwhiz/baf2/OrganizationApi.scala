package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import org.bson.types.ObjectId

object OrganizationApi {

  def organizationsByIds(organizationOids: Seq[ObjectId]): Seq[DynDoc] =
    BWMongoDB3.organizations.find(Map("_id" -> Map($in -> organizationOids)))

  def organizationById(organizationOid: ObjectId): DynDoc =
    BWMongoDB3.organizations.find(Map("_id" -> organizationOid)).head

  def organizationByName(orgName: String): Option[DynDoc] =
    BWMongoDB3.organizations.find(Map("name" -> orgName)).headOption

  def exists(organizationOid: ObjectId): Boolean = BWMongoDB3.organizations.find(Map("_id" -> organizationOid)).nonEmpty

  def fetch(oid: Option[ObjectId] = None, name: Option[String] = None, skill: Option[String] = None): Seq[DynDoc] =
      (oid, name, skill) match {
    case (Some(theOid), _, _) => BWMongoDB3.organizations.find(Map("_id" -> theOid))
    case (None, Some(theName), _) => BWMongoDB3.organizations.find(Map("name" -> theName))
    case (None, None, Some(theSkill)) => BWMongoDB3.organizations.find(Map("skills" -> theSkill))
    case _ => BWMongoDB3.organizations.find()
  }

  def validateNewName(newOrgName: String): Boolean = {
    val orgNameLength = newOrgName.length
    if (newOrgName.trim.length != orgNameLength)
      throw new IllegalArgumentException(s"Bad organization name (has blank padding): '$newOrgName'")
    if (orgNameLength > 150 || orgNameLength < 5)
      throw new IllegalArgumentException(s"Bad organization name length: $orgNameLength")
    if (OrganizationApi.fetch(name=Some(newOrgName)).nonEmpty)
      throw new IllegalArgumentException(s"Organization named '$newOrgName' already exists")
    true
  }

}
