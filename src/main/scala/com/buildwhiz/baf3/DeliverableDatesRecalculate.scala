package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

import java.io.PrintWriter
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DeliverableDatesRecalculate extends HttpServlet with HttpUtils {

  private var phaseRecord: Option[DynDoc] = None
  private var writer: Option[PrintWriter] = None
  private def write(s: String): Unit = writer.foreach(_.print(s))
  private lazy val activities = phaseRecord.map(p => PhaseApi.allActivities(p._id[ObjectId])).get
  private var phaseStartDate: Long = 0
  private lazy val deliverablesByOid: Map[ObjectId, DynDoc] = {
    deliverableOids.zip(deliverables).toMap
  }
  private lazy val constraintsByOwnerOid: Map[ObjectId, Seq[DynDoc]] = {
    constraints.groupBy(_.owner_deliverable_id[ObjectId])
  }
  private lazy val procurementsByOid: Map[ObjectId, DynDoc] = {
    val procurements: Seq[DynDoc] =
        BWMongoDB3.procurements.find(Map("activity_id" -> Map($in -> activities.map(_._id[ObjectId]))))
    procurements.map(_._id[ObjectId]).zip(procurements).toMap
  }
  private lazy val keyDataByOid: Map[ObjectId, DynDoc] = {
    val project = phaseRecord.map(p => PhaseApi.parentProject(p._id[ObjectId])).get
    val keyData: Seq[DynDoc] = BWMongoDB3.key_data.find(Map("project_id" -> project._id[ObjectId]))
    keyData.map(_._id[ObjectId]).zip(keyData).toMap
  }
  private lazy val deliverables = DeliverableApi.deliverablesByActivityOids(activities.map(_._id[ObjectId]))
  private lazy val deliverableOids = deliverables.map(_._id[ObjectId])
  private lazy val constraints: Seq[DynDoc] = {
    val constraintsQuery = Map($and -> Seq(Map("owner_deliverable_id" -> Map($in -> deliverableOids)),
      Map("constraint_id" -> Map($in -> deliverableOids))))
    BWMongoDB3.constraints.find(constraintsQuery)
  }

  private def addDaysToDate(date: Long, days: Int): Long = {
    date + days * 86400 * 1000
  }

  private def earliestStartDate(deliverable: DynDoc, level: Int): Long = {
    write("  " * level + s"START: ${deliverable.name[String]}")
    val constraints = constraintsByOwnerOid(deliverable._id[ObjectId])
    val constraintEndDates: Seq[Long] = constraints.map(constraint => {
      constraint.`type`[String] match {
        case "Document" | "Work" =>
          val constraintDeliverable = deliverablesByOid(constraint.constraint_id[ObjectId])
          traverseOneTree(constraintDeliverable, level + 1)
        case "Material" | "Labor" | "Equipment" =>
          val procurementRecord = procurementsByOid(constraint.constraint_id[ObjectId])
          val procurementDate = procurementRecord.get[Int]("duration") match {
            case Some(d) => addDaysToDate(phaseStartDate, d)
            case None => phaseStartDate
          }
          write("  " * (level + 1) + s"PROCUREMENT: ${procurementRecord.name[String]} = $procurementDate")
          procurementDate
        case "Data" =>
          val keyDataRecord = keyDataByOid(constraint.constraint_id[ObjectId])
          val keyDataDate = keyDataRecord.get[Int]("duration") match {
            case Some(d) => addDaysToDate(phaseStartDate, d)
            case None => phaseStartDate
          }
          write("  " * (level + 1) + s"DATA: ${keyDataRecord.name[String]} = $keyDataDate")
          keyDataDate
      }
    })
    val dateStart = if (constraints.nonEmpty) {
      constraintEndDates.max
    } else {
      phaseStartDate
    }
    write("  " * level + s"END: ${deliverable.name[String]}")
    dateStart
  }

  private def traverseOneTree(deliverable: DynDoc, level: Int): Long = {
    write("  " * level + s"START: ${deliverable.name[String]}")
    def setEndDate(deliverable: DynDoc, date: Long): Unit = {
      val updateResult = BWMongoDB3.deliverables.updateOne(Map("_id" -> deliverable._id[ObjectId]),
          Map($set -> ("date_end_estimated" -> date)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }
    val dateEnd = deliverable.get[Long]("date_end_actual") match {
      case Some(dateEndActual) =>
        dateEndActual
      case _ =>
        val esd = earliestStartDate(deliverable, level)
        val estimatedEndDate = addDaysToDate(esd, deliverable.duration[Int])
        deliverable.get[Long]("date_end_estimated") match {
          case Some(des) =>
            if (des != estimatedEndDate) {
              setEndDate(deliverable, estimatedEndDate)
            }
          case None =>
            setEndDate(deliverable, estimatedEndDate)
        }
        estimatedEndDate
    }
    write("  " * level + s"END: ${deliverable.name[String]} = $dateEnd")
    dateEnd
  }

  private def traverseAllTrees(): Document = {
    val t0 = System.currentTimeMillis
    val constraintsByConstraintOid = constraints.groupBy(_.constraint_id[ObjectId])
    val endDeliverables = deliverables.filter(d => !constraintsByConstraintOid.contains(d._id[ObjectId]))
    write(s"""${endDeliverables.length} End-Deliverables: ${endDeliverables.map(_.name[String]).mkString(", ")}""")
    for (endDeliverable <- endDeliverables) {
      traverseOneTree(endDeliverable, 1)
    }
    val leafDeliverables = deliverables.filter(d => !constraintsByOwnerOid.contains(d._id[ObjectId]))
    //val soloDeliverables = endDeliverables.filter(d => leafDeliverables.map(_._id[ObjectId]).contains(d._id[ObjectId]))
    val delay = System.currentTimeMillis - t0
    write(s"time: $delay ms")
    val resutlDoc: Document = Map(
        //"solo_deliverables" ->
        //  soloDeliverables.map(d => Map("name" -> d.name[String], "type" -> d.deliverable_type[String])),
        "end_deliverables" ->
          endDeliverables.map(d => Map("name" -> d.name[String], "type" -> d.deliverable_type[String])),
        "leaf_deliverables" ->
          leafDeliverables.map(d => Map("name" -> d.name[String], "type" -> d.deliverable_type[String])),
        "milliseconds" -> delay)
    resutlDoc
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      writer = Some(response.getWriter)
      val parameters = getParameterMap(request)
      val phaseOid = new ObjectId(parameters("phase_id"))
      phaseRecord = Some(PhaseApi.phaseById(phaseOid))
      val timestamps: Option[DynDoc] = phaseRecord.map(_.timestamps[Document])
      timestamps.flatMap(_.get[Long]("date_start_estimated")) match {
        case Some(dse) => phaseStartDate = dse
          val result = phaseRecord.map(_ => traverseAllTrees()).get
          //response.getWriter.print(result.toJson)
          response.setContentType("text/plain")
          response.setStatus(HttpServletResponse.SC_OK)
          BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
        case None =>
          BWLogger.log(getClass.getName, request.getMethod, "EXIT-WARN: phase start-date undefined", request)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

