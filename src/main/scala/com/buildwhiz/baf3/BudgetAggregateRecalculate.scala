package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import com.mongodb.client.model.UpdateOneModel
import org.bson.Document
import org.bson.types.{Decimal128, ObjectId}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.jdk.CollectionConverters._

class BudgetAggregateRecalculate extends HttpServlet with HttpUtils with DateTimeUtils {

  private def updateDeliverablesCurrentBudgets(deliverables: Seq[DynDoc]): (Int, Int) = {
    val bulkWritesBuffer: Many[UpdateOneModel[Document]] = deliverables.map(deliverable => {
      val selector = new Document("budget_contracted", new Document("$exists", true)).
          append("_id", deliverable._id[ObjectId])
      new UpdateOneModel[Document](selector,
          new Document($set, new Document("budget_current", deliverable.budget_contracted[Decimal128])))
    }).asJava
    val result = BWMongoDB3.deliverables.bulkWrite(bulkWritesBuffer)
    (result.getMatchedCount, result.getModifiedCount)
  }

  private def deliverableGroupsToBulkWrites(dg: Map[ObjectId, Seq[DynDoc]], includeEstimate: Boolean = true):
      Many[UpdateOneModel[Document]] = {

    def sumOptDecimals(optDecimals: Seq[Option[Decimal128]]): Decimal128 = {
      val bdZero = BigDecimal.decimal(0).bigDecimal
      new Decimal128(optDecimals.map {case Some(b) => b.bigDecimalValue(); case None => bdZero}.
        foldLeft(bdZero)((a, b) => a.add(b)))
    }

    val budgetUpdates = dg.map(deliverablesByGroup => {
      val groupDeliverables: Seq[DynDoc] = deliverablesByGroup._2
      val mongoOid: ObjectId = deliverablesByGroup._1
      val budgetEstimatedValues = groupDeliverables.map(_.get[Decimal128]("budget_estimated"))
      val budgetContractedValues = groupDeliverables.map(_.get[Decimal128]("budget_contracted"))
      val budgetCurrentValues = groupDeliverables.map(_.get[Decimal128]("budget_current"))
      val budgetEstimated = sumOptDecimals(budgetEstimatedValues)
      val budgetContracted = sumOptDecimals(budgetContractedValues)
      val budgetCurrent = sumOptDecimals(budgetCurrentValues)
      val estimatedCounts = budgetEstimatedValues.count(_.nonEmpty)
      val contractedCounts = budgetContractedValues.count(_.nonEmpty)
      val currentCounts =budgetCurrentValues.count(_.nonEmpty)
      val deliverableCount = groupDeliverables.length
      (mongoOid, budgetEstimated, estimatedCounts, budgetContracted, contractedCounts, budgetCurrent,
        currentCounts, deliverableCount)
    }).toSeq

    val bulkWritesBuffer: Many[UpdateOneModel[Document]] = budgetUpdates.map(update => {
      val values: Document = Map(
        "budget_contracted" -> update._4, "budget_contracted_count" -> update._5,
        "budget_current" -> update._6, "budget_current_count" -> update._7, "budget_items_count" -> update._8
      )
      if (includeEstimate) {
        values.append("budget_estimated", update._2).append("budget_estimated_count", update._3)
      }
      new UpdateOneModel[Document](new Document("_id", update._1), new Document($set, values))
    }).asJava

    bulkWritesBuffer
  }

  private def updateProjects(deliverables: Seq[DynDoc]): (Int, Int) = {
    val projectBulkWrites = deliverableGroupsToBulkWrites(deliverables.groupBy(_.project_id[ObjectId]),
        includeEstimate = false)
    val result = BWMongoDB3.projects.bulkWrite(projectBulkWrites)
    (result.getMatchedCount, result.getModifiedCount)
  }

  private def updatePhases(deliverables: Seq[DynDoc]): (Int, Int) = {
    val phaseBulkWrites = deliverableGroupsToBulkWrites(deliverables.groupBy(_.phase_id[ObjectId]),
        includeEstimate = false)
    val result = BWMongoDB3.phases.bulkWrite(phaseBulkWrites)
    (result.getMatchedCount, result.getModifiedCount)
  }

  private def updateProcesses(deliverables: Seq[DynDoc]): (Int, Int) = {
    val processBulkWrites = deliverableGroupsToBulkWrites(deliverables.groupBy(_.process_id[ObjectId]))
    val result = BWMongoDB3.processes.bulkWrite(processBulkWrites)
    (result.getMatchedCount, result.getModifiedCount)
  }

  private def updateTasks(deliverables: Seq[DynDoc]): (Int, Int) = {
    val taskBulkWrites = deliverableGroupsToBulkWrites(deliverables.groupBy(_.activity_id[ObjectId]))
    val result = BWMongoDB3.tasks.bulkWrite(taskBulkWrites)
    (result.getMatchedCount, result.getModifiedCount)
  }

  private def aggregateDeliverables(request: HttpServletRequest): String = {
    val parameters = getParameterMap(request)
    val projectOid = new ObjectId(parameters("project_id"))
    val deliverables = BWMongoDB3.deliverables.find(Map("project_id" -> projectOid, "process_type" -> "Primary"))

    val deliverableUpdateResult =updateDeliverablesCurrentBudgets(deliverables)
    val deliverableMessage = s"${deliverableUpdateResult._2} of ${deliverableUpdateResult._1} deliverables"
    val taskUpdateResult = updateTasks(deliverables)
    val taskMessage = s"${taskUpdateResult._2} of ${taskUpdateResult._1} tasks"
    val processUpdateResult = updateProcesses(deliverables)
    val processMessage = s"${processUpdateResult._2} of ${processUpdateResult._1} processes"
    val phaseUpdateResult = updatePhases(deliverables)
    val phaseMessage = s"${phaseUpdateResult._2} of ${phaseUpdateResult._1} phases"
    val projectUpdateResult = updateProjects(deliverables)
    val projectMessage = s"${projectUpdateResult._2} of ${projectUpdateResult._1} projects"

    s"$deliverableMessage, $taskMessage, $processMessage, $phaseMessage, $projectMessage"
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val t0 = System.currentTimeMillis()
      val message = aggregateDeliverables(request)
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms) Updated: $message", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val t0 = System.currentTimeMillis()
      val message = aggregateDeliverables(request)
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms) Updated: $message", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace(response.getWriter)
      throw t
    }
  }

}

