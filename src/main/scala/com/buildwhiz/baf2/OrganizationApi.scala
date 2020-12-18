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

  def fetch(optOid: Option[ObjectId] = None, optName: Option[String] = None, optSkill: Option[String] = None,
            optOrgType: Option[String] = None): Seq[DynDoc] = (optOid, optName, optSkill, optOrgType) match {
    case (Some(theOid), _, _, None) => BWMongoDB3.organizations.find(Map("_id" -> theOid))
    case (Some(theOid), _, _, Some(orgType)) => BWMongoDB3.organizations.find(Map("_id" -> theOid, "type" -> orgType))
    case (None, Some(theName), _, None) => BWMongoDB3.organizations.find(Map("name" -> theName))
    case (None, Some(theName), _, Some(orgType)) => BWMongoDB3.organizations.
        find(Map("name" -> theName, "type" -> orgType))
    case (None, None, Some(theSkill), None) => BWMongoDB3.organizations.find(Map("skills" -> theSkill))
    case (None, None, Some(theSkill), Some(orgType)) => BWMongoDB3.organizations.
        find(Map("skills" -> theSkill, "type" -> orgType))
    case (None, None, None, Some(orgType)) => BWMongoDB3.organizations.find(Map("organization_type" -> orgType))
    case _ => BWMongoDB3.organizations.find()
  }

  def validateNewName(newOrgName: String): Boolean = {
    val orgNameLength = newOrgName.length
    if (newOrgName.trim.length != orgNameLength)
      throw new IllegalArgumentException(s"Bad organization name (has blank padding): '$newOrgName'")
    if (orgNameLength > 150 || orgNameLength < 5)
      throw new IllegalArgumentException(s"Bad organization name length: $orgNameLength")
    if (OrganizationApi.fetch(optName=Some(newOrgName)).nonEmpty)
      throw new IllegalArgumentException(s"Organization named '$newOrgName' already exists")
    true
  }

}
