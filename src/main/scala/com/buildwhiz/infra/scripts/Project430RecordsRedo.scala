package com.buildwhiz.infra.scripts

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.BWLogger
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

object Project430RecordsRedo extends App {
  BWLogger.log(getClass.getName, "main()", "ENTRY")

  private val defaultName = "430 Forest"

  private def createActivity(): ObjectId = {
    BWMongoDB3.projects.find(Map("name" -> defaultName)).asScala.headOption match {
      case Some(project) => println(s"'$defaultName' EXISTS ($project)")
        if (args.nonEmpty && args(0).matches("init(ialize)?")) {
          println("DELETING existing project")
          BWMongoDB3.projects.deleteOne(Map("name" -> defaultName))
          createProject()
        }
      case None => createProject()
    }
    println(s"CREATING new phase '$defaultName'")
    val now = System.currentTimeMillis
    val defaultPhase: Document = Map("name" -> defaultName, "timestamps" -> Map("created" -> now),
      "activity_ids" -> Seq.empty[Document], "variables" -> Seq.empty[Document], "timers" -> Seq.empty[Document],
      "status" -> "defined", "admin_person_id" -> "", /*"bpmn_name" -> "", */"bpmn_timestamps" -> Seq.empty[Document])
    BWMongoDB3.phases.insertOne(defaultPhase)
    val thePhase: DynDoc = BWMongoDB3.phases.find(Map("name" -> defaultName)).head
    println(s"INSERTED phase: $thePhase")
    thePhase._id[ObjectId]
  }

  private def createPhase(): ObjectId = {
    BWLogger.log(getClass.getName, "createPhase()", s"CREATING phase '$defaultName'")
    println(s"CREATING phase '$defaultName'")
    val now = System.currentTimeMillis
    val defaultPhase: Document = Map("name" -> defaultName, "timestamps" -> Map("created" -> now, "start" -> now),
      "activity_ids" -> Seq.empty[Document], "variables" -> Seq.empty[Document], "timers" -> Seq.empty[Document],
      "status" -> "running", "admin_person_id" -> new ObjectId("56f124dfd5d8ad25b1325b3e"), "bpmn_name" -> "****",
      "bpmn_timestamps" -> Seq.empty[Document])
    BWMongoDB3.phases.insertOne(defaultPhase)
    val thePhase: DynDoc = BWMongoDB3.phases.find(Map("name" -> defaultName)).head
    BWLogger.log(getClass.getName, "createPhase()", s"INSERTED phase: $thePhase")
    println(s"INSERTED phase: $thePhase")
    thePhase._id[ObjectId]
  }

  private def createProject(): Unit = {
    BWLogger.log(getClass.getName, "createProject()", "CREATING project '430 Forest'")
    println("CREATING project '430 Forest'")
    val phaseOid = createPhase()
    val now = System.currentTimeMillis
    val forest430: Document = Map("_id" -> project430ForestOid, "name" -> defaultName,
      "timestamps" -> Map("created" -> now, "start" -> now), "phase_ids" -> Seq(phaseOid).asJava,
      "status" -> "running", "admin_person_id" -> new ObjectId("56f124dfd5d8ad25b1325b3e"), "ver" -> "1.01")
    BWMongoDB3.projects.insertOne(forest430)
    val msg = s"INSERTED project: ${BWMongoDB3.projects.find(Map("name" -> defaultName)).head}"
    BWLogger.log(getClass.getName, "createProject()", msg)
    println(msg)
  }

  BWMongoDB3.projects.find(Map("name" -> defaultName)).asScala.headOption match {
    case Some(project) => println(s"'$defaultName' EXISTS ($project)")
      if (args.nonEmpty && args(0).matches("init(ialize)?")) {
        BWLogger.log(getClass.getName, "main()", "DELETING existing project")
        println("DELETING existing project")
        BWMongoDB3.projects.deleteOne(Map("name" -> defaultName))
        createProject()
      }
    case None => createProject()
  }

  BWLogger.log(getClass.getName, "main()", "EXIT")
}
