package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import org.bson.Document
import org.bson.types.ObjectId

object OrganizationApi {

  def organizationsByIds(organizationOids: Seq[ObjectId]): Seq[DynDoc] =
    BWMongoDB3.organizations.find(Map("_id" -> Map($in -> organizationOids)))

  def organizationById(organizationOid: ObjectId): DynDoc =
      BWMongoDB3.organizations.find(Map("_id" -> organizationOid)).head

  def exists(organizationOid: ObjectId): Boolean = BWMongoDB3.organizations.find(Map("_id" -> organizationOid)).nonEmpty

  def fetch(oid: Option[ObjectId] = None, name: Option[String] = None, skill: Option[String] = None): Seq[DynDoc] =
      (oid, name, skill) match {
    case (Some(theOid), _, _) => BWMongoDB3.organizations.find(Map("_id" -> theOid))
    case (None, Some(theName), _) => BWMongoDB3.organizations.find(Map("name" -> theName))
    case (None, None, Some(theSkill)) => BWMongoDB3.organizations.find(Map("skills" -> theSkill))
    case _ => BWMongoDB3.organizations.find()
  }

}
