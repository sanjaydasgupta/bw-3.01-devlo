package com.buildwhiz.tools.scripts

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.mongodb.client.model.InsertOneModel
import org.bson.Document

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object CopyActivitiesToTasks {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.setContentType("text/plain")
    val writer = response.getWriter
    val go = args.length > 0 && args.head == "GO"
    val knownActivities: Seq[DynDoc] = BWMongoDB3.tasks.find()
    val activityCount = knownActivities.length
    writer.println(s"found $activityCount activities")

    val knownTasks: Seq[DynDoc] = BWMongoDB3.tasks.find()
    writer.println(s"found ${knownTasks.length} tasks")
//    val badDeliverables: Seq[DynDoc] = BWMongoDB3.deliverables.find(Map("activity_id" -> Map($exists -> false)))
//    writer.println(s"found ${badDeliverables.length} bad deliverables")
    if (knownTasks.nonEmpty) {
      writer.println(s"${knownTasks.length} Tasks found ... Exiting")
    } else if (go) {
//      if (badDeliverables.nonEmpty) {
//        val deleteResult = BWMongoDB3.deliverables.deleteMany(Map("activity_id" -> Map($exists -> false)))
//        writer.println(s"Deleted ${deleteResult.getDeletedCount} bad deliverables")
//      }
      writer.println(s"Creating 'tasks' collection with $activityCount tasks ...")
      val bulkWriteBuffer: Many[InsertOneModel[Document]] = knownActivities.map(a => new InsertOneModel(a.asDoc))
      val bulkWriteResult = BWMongoDB3.tasks.bulkWrite(bulkWriteBuffer)
      writer.println(s"${getClass.getName}: Inserted ${bulkWriteResult.getInsertedCount}/$activityCount tasks")
      if (bulkWriteResult.getInsertedCount != activityCount) {
        writer.println(s"Restult info: $bulkWriteResult")
      }
    }
  }

}
