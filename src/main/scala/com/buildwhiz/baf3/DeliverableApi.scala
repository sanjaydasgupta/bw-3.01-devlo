package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._

import org.bson.types.ObjectId

object DeliverableApi {

  def deliverableById(deliverableOid: ObjectId): DynDoc = BWMongoDB3.deliverables.find(Map("_id" -> deliverableOid)).head

}
