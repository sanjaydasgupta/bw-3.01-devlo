package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import org.bson.types.ObjectId

object TeamApi {

  def teamById(teamOid: ObjectId): DynDoc = BWMongoDB3.teams.find(Map("_id" -> teamOid)).head

}
