package com.buildwhiz.api

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import org.bson.Document
import org.bson.types.ObjectId

object Action {

  def actionUsers(action: DynDoc): Seq[ObjectId] = {
    if (action.has("assigned_roles"))
      (action.assignee_person_id[ObjectId] +:
        action.assigned_roles[Many[Document]].map(_.person_id[ObjectId])).distinct
    else
      Seq(action.assignee_person_id[ObjectId])
  }

}
