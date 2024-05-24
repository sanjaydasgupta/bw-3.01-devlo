package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import com.mongodb.client.model.UpdateOneModel
import org.bson.Document
import org.bson.types.{Decimal128, ObjectId}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.language.implicitConversions

class BudgetAggregateRecalculate extends HttpServlet with HttpUtils with DateTimeUtils {

  implicit def doubleToDecimal128(d: Double): Decimal128 = {
    val dstr = f"$d%1.2f"
    Decimal128.parse(dstr)
  }

  private def sumOptionalDecimals(optDecimals: Seq[Option[Decimal128]]): Decimal128 = {
    val total = optDecimals.map {case Some(b) => b.doubleValue(); case None => 0.0 } . sum
    total
  }

  private def updateDeliverablesCurrentBudgets(deliverables: Seq[DynDoc]): (Int, Int) = {

    val coAggrPipe: Many[Document] = Seq(
      Map("$match" -> Map("deliverable_id" -> Map($in -> deliverables.map(_._id[ObjectId])))),
      Map("$group" -> Map("_id" -> "$deliverable_id", "change_orders_value" -> Map("$sum" -> "$budget_contracted"),
        "change_orders_count" -> Map("$count" -> Map()))),
    ).map(_.asDoc)

    val deliverableChangeInfo: Seq[DynDoc] = BWMongoDB3.deliverable_change_orders.aggregate(coAggrPipe)
    val changesByDeliverable = deliverableChangeInfo.map(dci => (dci._id[ObjectId], dci)).toMap

    val bulkWritesBuffer: Many[UpdateOneModel[Document]] = deliverables.filter(_.has("budget_contracted")).map(dd => {
      val deliverableOid = dd._id[ObjectId]
      val setterValues = changesByDeliverable.get(deliverableOid) match {
        case None => new Document("budget_current", dd.budget_contracted[Decimal128])
        case Some(changes) =>
          val budgetCurrent: Decimal128 = dd.budget_contracted[Decimal128].doubleValue() +
              changes.change_orders_value[Decimal128].doubleValue()
          new Document("budget_current", budgetCurrent).
              append("change_orders_value", changes.change_orders_value[Decimal128]).
              append("change_orders_count", changes.change_orders_count[Int])
      }
      new UpdateOneModel[Document](Map("_id" -> deliverableOid), Map($set -> setterValues))
    })
    if (bulkWritesBuffer.nonEmpty) {
      val result = BWMongoDB3.deliverables.bulkWrite(bulkWritesBuffer)
      (result.getMatchedCount, result.getModifiedCount)
    } else {
      (0, 0)
    }
  }

  private def deliverableGroupsToBulkWrites(dg: Map[ObjectId, Seq[DynDoc]], includeEstimate: Boolean = true):
      Many[UpdateOneModel[Document]] = {

    def sumOptDoubles(optDecimals: Seq[Option[Double]]): Double = {
      optDecimals.map {case Some(b) => b; case None => 0.0}.sum
    }

    val budgetUpdates = dg.map(deliverablesByGroup => {
      val groupDeliverables: Seq[DynDoc] = deliverablesByGroup._2
      val mongoOid: ObjectId = deliverablesByGroup._1
      val changeOrderCountValues = groupDeliverables.map(_.get[Int]("change_orders_count"))
      val changeOrderCounts = changeOrderCountValues.flatten.sum
      val budgetChangeValues = groupDeliverables.map(_.get[Decimal128]("change_orders_value"))
      val budgetChangeOrders = sumOptionalDecimals(budgetChangeValues)
      val budgetEstimatedValues = groupDeliverables.map(_.get[Decimal128]("budget_estimated"))
      val budgetEstimated = sumOptionalDecimals(budgetEstimatedValues)
      val estimatedCounts = budgetEstimatedValues.count(_.nonEmpty)
      val budgetContractedValues = groupDeliverables.map(_.get[Decimal128]("budget_contracted"))
      val budgetContracted = sumOptionalDecimals(budgetContractedValues)
      val contractedCounts = budgetContractedValues.count(_.nonEmpty)
      val budgetCurrentValues = groupDeliverables.map(_.get[Decimal128]("budget_current"))
      val budgetCurrent = sumOptionalDecimals(budgetCurrentValues)
      val currentCounts = budgetCurrentValues.count(_.nonEmpty)
      val percentCompleteValues = groupDeliverables.map(_.get[Decimal128]("percent_complete"))
      val wtdPctCompleteValues: Seq[Option[Double]] = percentCompleteValues.zip(budgetCurrentValues).map {
          case (Some(pc), Some(bc)) => Some(pc.doubleValue() * bc.doubleValue())
          case _ => None
      }
      val percentCompleteCounts = wtdPctCompleteValues.count(_.nonEmpty)
      val percentComplete: Decimal128 = if (percentCompleteCounts == 0) {
        0.0
      } else {
        val pctCompleteDouble: Double = sumOptDoubles(wtdPctCompleteValues) / budgetCurrent.doubleValue()
        pctCompleteDouble
      }
      val deliverableCount = groupDeliverables.length
      (mongoOid, budgetEstimated, estimatedCounts, budgetContracted, contractedCounts, budgetCurrent,
        currentCounts, deliverableCount, percentComplete, percentCompleteCounts, budgetChangeOrders, changeOrderCounts)
    }).toSeq

    val bulkWritesBuffer: Many[UpdateOneModel[Document]] = budgetUpdates.map(update => {
      val changeOrdersCount = update._12
      val values: Document = Map(
        "budget_contracted" -> update._4, "budget_contracted_count" -> update._5,
        "budget_current" -> update._6, "budget_current_count" -> update._7, "budget_items_count" -> update._8,
        "percent_complete" -> update._9, "percent_complete_count" -> update._10
      )
      if (changeOrdersCount != 0) {
        values.append("change_orders_value", update._11).append("change_orders_count", changeOrdersCount)
      }
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
    val deliverables = BWMongoDB3.deliverables.find(Map("project_id" -> projectOid, "process_type" -> "Primary",
      "status" -> Map($ne -> "Deliverable-Bypassed"), "deliverable_type" -> Map($regex -> "Work|Document")/*,
      "budget_contracted" -> Map($exists -> true)*/))
    if (deliverables.nonEmpty) {
      val deliverableUpdateResult = updateDeliverablesCurrentBudgets(deliverables)
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
    } else {
      "No applicable deliverables found!"
    }
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