package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import com.mongodb.client.model.UpdateOneModel
import org.bson.Document
import org.bson.types.{Decimal128, ObjectId}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class BudgetAggregateRecalculate extends HttpServlet with HttpUtils with DateTimeUtils {

  private def updateDeliverablesCurrentBudgets(deliverables: Seq[DynDoc]): (Int, Int) = {

    val currentBudgetPipeline: Many[Document] = Seq(
      Map("$match" -> Map("budget_contracted" -> Map("$exists" -> true), "_id" -> Map($in -> deliverables.map(_._id[ObjectId])))),
      Map("$lookup" -> Map("from" -> "deliverable_change_orders", "as" -> "changes", "localField" -> "_id",
        "foreignField" -> "deliverable_id")),
      Map("$project" -> Map("budget_contracted" -> true,
        "budget_change_order" -> Map("$sum" -> "$changes.budget_contracted"),
        "change_order_count" -> Map("$size" -> "$changes.budget_contracted"))),
      Map("$project" -> Map("budget_contracted" -> true, "budget_change_order" -> true, "change_order_count" -> true,
        "budget_current" -> Map("$sum" -> Seq("$budget_contracted", "$budget_change_order"))))
    ).map(mm => {val dd: Document = mm; dd})
    val budgetCurrentValues: Seq[DynDoc] = BWMongoDB3.deliverables.aggregate(currentBudgetPipeline)

    val bulkWritesBuffer: Many[UpdateOneModel[Document]] = budgetCurrentValues.map(bcv => {
      val updateParameter: Document = Map($set -> Map("budget_current" -> bcv.budget_current[Decimal128]))
      if (bcv.change_order_count[Int] != 0) {
        updateParameter.get($set).asInstanceOf[Document].
            append("budget_change_order", bcv.budget_change_order[Decimal128]).
            append("change_order_count", bcv.change_order_count[Int])
      }
      new UpdateOneModel[Document](Map("_id" -> bcv._id[ObjectId]), updateParameter)
    })
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
      val changeOrderCountValues = groupDeliverables.map(_.get[Int]("change_order_count"))
      val budgetChangeValues = groupDeliverables.map(_.get[Decimal128]("budget_change_order"))
      val budgetEstimatedValues = groupDeliverables.map(_.get[Decimal128]("budget_estimated"))
      val budgetContractedValues = groupDeliverables.map(_.get[Decimal128]("budget_contracted"))
      val budgetCurrentValues = groupDeliverables.map(_.get[Decimal128]("budget_current"))
      val percentCompleteValues = groupDeliverables.map(_.get[Decimal128]("percent_complete"))
      val weightedPercentCompleteValues: Seq[Option[Decimal128]] = percentCompleteValues.zip(budgetCurrentValues).
        map {case (Some(pc), Some(bc)) => Some(pc.bigDecimalValue().multiply(bc.bigDecimalValue())); case _ => None}.
        map(obd => obd.map(bd => new Decimal128(bd)))
      val percentComplete = sumOptDecimals(weightedPercentCompleteValues)
      val percentCompleteCounts = weightedPercentCompleteValues.count(_.nonEmpty)
      val changeOrderCounts = changeOrderCountValues.flatten.sum
      val budgetChangeOrders = sumOptDecimals(budgetChangeValues)
      val budgetEstimated = sumOptDecimals(budgetEstimatedValues)
      val budgetContracted = sumOptDecimals(budgetContractedValues)
      val budgetCurrent = sumOptDecimals(budgetCurrentValues)
      val estimatedCounts = budgetEstimatedValues.count(_.nonEmpty)
      val contractedCounts = budgetContractedValues.count(_.nonEmpty)
      val currentCounts = budgetCurrentValues.count(_.nonEmpty)
      val deliverableCount = groupDeliverables.length
      (mongoOid, budgetEstimated, estimatedCounts, budgetContracted, contractedCounts, budgetCurrent,
        currentCounts, deliverableCount, percentComplete, percentCompleteCounts, budgetChangeOrders, changeOrderCounts)
    }).toSeq

    val bulkWritesBuffer: Many[UpdateOneModel[Document]] = budgetUpdates.map(update => {
      val values: Document = Map(
        "budget_contracted" -> update._4, "budget_contracted_count" -> update._5,
        "budget_current" -> update._6, "budget_current_count" -> update._7, "budget_items_count" -> update._8,
        "percent_complete" -> update._9, "percent_complete_count" -> update._10, "budget_change_order" -> update._11,
        "change_order_count" -> update._12
      )
      if (includeEstimate) {
        values.append("budget_estimated", update._2).append("budget_estimated_count", update._3)
      }
      new UpdateOneModel[Document](new Document("_id", update._1), new Document($set, values))
    })

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
      response.setContentType("text/plain")
      val t0 = System.currentTimeMillis()
      val message = aggregateDeliverables(request)
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms) Updated: $message", request)
      response.getWriter.println(s"EXIT-OK (time: $delay ms) Updated: $message")
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