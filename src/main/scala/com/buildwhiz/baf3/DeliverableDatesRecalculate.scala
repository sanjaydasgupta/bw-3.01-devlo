package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

import java.io.PrintWriter
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DeliverableDatesRecalculate extends HttpServlet with HttpUtils with DateTimeUtils {

  private var writer: Option[PrintWriter] = None
  private def respond(s: String): Unit = writer.foreach(_.print(s))
  private var verboseSetting: Option[Boolean] = Some(true)
  private val verbose = true
//  private lazy val verbose = verboseSetting match {
//    case Some(true) => true
//    case _ => false
//  }

  private var phaseRecord: Option[DynDoc] = None
  private lazy val activities = phaseRecord.map(p => PhaseApi.allActivities(p._id[ObjectId])).get
  private var phaseStartDate: Long = 0

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
  private lazy val deliverablesByOid: Map[ObjectId, DynDoc] = {
    deliverableOids.zip(deliverables).toMap
  }

  private lazy val constraints: Seq[DynDoc] = {
    BWMongoDB3.constraints.find(Map("owner_deliverable_id" -> Map($in -> deliverableOids)))
  }
  private lazy val constraintsByOwnerOid: Map[ObjectId, Seq[DynDoc]] = {
    constraints.groupBy(_.owner_deliverable_id[ObjectId])
  }

  private def addDaysToDate(date: Long, days: Int): Long = {
    date + days * 86400L * 1000L
  }
  private def msToDate(ms: Long): String = {
    val phaseTimeZone = phaseRecord.map(p => PhaseApi.timeZone(p)).get
    dateString(ms, phaseTimeZone)
  }

  private def earliestStartDate(deliverable: DynDoc, level: Int): Long = {
    if (verbose)
      respond("&nbsp;&nbsp;&nbsp;&nbsp;" * level + s"ENTRY EarliestStartDate(${deliverable.name[String]}) (${deliverable._id[ObjectId]})<br/>")
    if (constraintsByOwnerOid.contains(deliverable._id[ObjectId])) {
      val constraints = constraintsByOwnerOid(deliverable._id[ObjectId])
      val constraintEndDates: Seq[Long] = constraints.map(constraint => {
        val constraintOid = constraint.constraint_id[ObjectId]
        constraint.`type`[String] match {
          case "Document" | "Work" =>
            if (deliverablesByOid.contains(constraintOid)) {
              val constraintDeliverable = deliverablesByOid(constraintOid)
              val deliverableDate = traverseOneTree(constraintDeliverable, level + 1)
              //if (verbose)
              //  respond("&nbsp;&nbsp;&nbsp;&nbsp;" * (level + 1) +
              //    s"DELIVERABLE: ${constraintDeliverable.name[String]} = ${msToDate(deliverableDate)}<br/>")
              deliverableDate
            } else {
              respond("""<font color="red">""" + "&nbsp;&nbsp;&nbsp;&nbsp;" * (level + 1) + s"MISSING constraint-deliverable: $constraintOid</font><br/>")
              -1
            }
          case "Material" | "Labor" | "Equipment" =>
            if (procurementsByOid.contains(constraintOid)) {
              val procurementRecord = procurementsByOid(constraintOid)
              val procurementDate = procurementRecord.get[Int]("duration") match {
                case Some(d) => addDaysToDate(phaseStartDate, d)
                case None => phaseStartDate
              }
              if (verbose)
                respond("&nbsp;&nbsp;&nbsp;&nbsp;" * (level + 1) +
                  s"PROCUREMENT: ${procurementRecord.name[String]} = ${msToDate(procurementDate)}<br/>")
              procurementDate
            } else {
              respond("""<font color="red">""" + "&nbsp;&nbsp;&nbsp;&nbsp;" * (level + 1) +
                  s"MISSING procurement: $constraintOid</font><br/>")
              -1
            }
          case "Data" =>
            if (keyDataByOid.contains(constraintOid)) {
              val keyDataRecord = keyDataByOid(constraintOid)
              val keyDataDate = keyDataRecord.get[Int]("duration") match {
                case Some(d) => addDaysToDate(phaseStartDate, d)
                case None => phaseStartDate
              }
              if (verbose)
                respond("&nbsp;&nbsp;&nbsp;&nbsp;" * (level + 1) +
                  s"DATA: ${keyDataRecord.name[String]} = ${msToDate(keyDataDate)}<br/>")
              keyDataDate
            } else {
              respond("""<font color="red">""" + "&nbsp;&nbsp;&nbsp;&nbsp;" * (level + 1) +
                  s"MISSING key-data: $constraintOid</font><br/>")
              -1
            }
        }
      }).filter(_ != -1)
      val dateStart = if (constraintEndDates.nonEmpty) {
        constraintEndDates.max
      } else {
        phaseStartDate
      }
      if (verbose)
        respond("&nbsp;&nbsp;&nbsp;&nbsp;" * level +
          s"EXIT EarliestStartDate(${deliverable.name[String]}) (${deliverable._id[ObjectId]}) = ${msToDate(dateStart)}<br/>")
      dateStart
    } else {
      if (verbose)
        respond("""<font color="blue">""" + "&nbsp;&nbsp;&nbsp;&nbsp;" * (level + 1) +
          s"MISSING constraints for deliverable: ${deliverable.name[String]} (${deliverable._id[ObjectId]})</font><br/>")
      if (verbose)
        respond("&nbsp;&nbsp;&nbsp;&nbsp;" * level +
          s"EXIT EarliestStartDate(${deliverable.name[String]}) (${deliverable._id[ObjectId]}) = ${msToDate(phaseStartDate)}<br/>")
      phaseStartDate
    }
  }

  private def traverseOneTree(deliverable: DynDoc, level: Int): Long = {
    if (verbose)
      respond("&nbsp;&nbsp;&nbsp;&nbsp;" * level +
        s"ENTRY TraverseOneTree(${deliverable.name[String]}) (${deliverable._id[ObjectId]})<br/>")
    def setEndDate(deliverable: DynDoc, date: Long): Unit = {
      val updateResult = BWMongoDB3.deliverables.updateOne(Map("_id" -> deliverable._id[ObjectId]),
          Map($set -> Map("date_end_estimated" -> date)))
      if (updateResult.getMatchedCount == 0) {
        respond("""<font color="red">""" + "&nbsp;&nbsp;&nbsp;&nbsp;" * (level + 1) +
            s"FAILED MongoDB update for ${deliverable.name[String]} (${deliverable._id[ObjectId]})</font><br/>")
      }
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
    if (verbose)
      respond("&nbsp;&nbsp;&nbsp;&nbsp;" * level +
        s"EXIT TraverseOneTree(${deliverable.name[String]}) (${deliverable._id[ObjectId]}) = ${msToDate(dateEnd)}<br/>")
    dateEnd
  }

  private def traverseAllTrees(): Document = {
    val t0 = System.currentTimeMillis
    val constraintsByConstraintOid = constraints.groupBy(_.constraint_id[ObjectId])
    val endDeliverables = deliverables.filter(d => !constraintsByConstraintOid.contains(d._id[ObjectId]))
    if (verbose)
      respond(s"""${endDeliverables.length} End-Deliverables: ${endDeliverables.map(_.name[String]).mkString(", ")}<br/><br/>""")
    for (endDeliverable <- endDeliverables) {
      traverseOneTree(endDeliverable, 0)
      if (verbose)
        respond("<br/>")
    }
    val leafDeliverables = deliverables.filter(d => !constraintsByOwnerOid.contains(d._id[ObjectId]))
    //val soloDeliverables = endDeliverables.filter(d => leafDeliverables.map(_._id[ObjectId]).contains(d._id[ObjectId]))
    val delay = System.currentTimeMillis - t0
    respond(s"<br/>time: $delay ms<br/>")
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
      //respond(s"BEFORE: verboseSetting: $verboseSetting")
      //respond(s"""BEFORE: parameters.get("verbose"): ${parameters.get("verbose")}""")
      verboseSetting = parameters.get("verbose") match {
        case None => Some(false)
        case Some(v) => Some(v.toBoolean)
      }
      //respond(s"AFTER: verboseSetting: $verboseSetting, verbose: $verbose")
      val phaseOid = new ObjectId(parameters("phase_id"))
      phaseRecord = Some(PhaseApi.phaseById(phaseOid))
      respond("<html><br/><tt>")
      if (verbose)
        respond(s"Deliverables: ${deliverables.length}, Constraints: ${constraints.length}<br/>")
      if (verbose)
        respond(s"Procurements: ${procurementsByOid.size}, KeyData: ${keyDataByOid.size}<br/><br/>")
      val timestamps: Option[DynDoc] = phaseRecord.map(_.timestamps[Document])
      timestamps.flatMap(_.get[Long]("date_start_estimated")) match {
        case Some(dse) => phaseStartDate = dse
          phaseRecord.map(_ => traverseAllTrees())
          //response.getWriter.print(result.toJson)
          response.setStatus(HttpServletResponse.SC_OK)
          BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
        case None =>
          BWLogger.log(getClass.getName, request.getMethod, "EXIT-WARN: phase start-date undefined", request)
      }
      respond("</tt></html>")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace(writer.get)
        //throw t
    }
  }

}

