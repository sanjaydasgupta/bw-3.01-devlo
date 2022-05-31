package com.buildwhiz.tools.scripts

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.mongodb.MongoNamespace
import com.mongodb.client.model.InsertOneModel
import org.bson.Document

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object CopyActivitiesToTasks {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.setContentType("text/plain")
    val writer = response.getWriter
    val go = args.length > 0 && args.head == "GO"
    val knownActivities: Seq[DynDoc] = BWMongoDB3.activities.find()
    val activityCount = knownActivities.length
    writer.println(s"found $activityCount activities")

    val knownTasks: Seq[DynDoc] = BWMongoDB3.tasks.find()
    writer.println(s"found ${knownTasks.length} tasks")
    if (knownTasks.nonEmpty) {
      writer.println(s"Exiting as ${knownTasks.length} tasks found")
    } else if (go) {
      writer.println(s"Creating 'tasks' collection with $activityCount tasks ...")
      val bulkWriteBuffer: Many[InsertOneModel[Document]] = knownActivities.map(a => new InsertOneModel(a.asDoc))
      val bulkWriteResult = BWMongoDB3.tasks.bulkWrite(bulkWriteBuffer)
      writer.println(s"${getClass.getName}: Inserted ${bulkWriteResult.getInsertedCount}/$activityCount tasks")
      if (bulkWriteResult.getInsertedCount != activityCount) {
        writer.println(s"Restult info: $bulkWriteResult")
      } else {
        BWMongoDB3.activities.renameCollection(new MongoNamespace("BuildWhiz", "old-activities"))
      }
    }
  }

}
