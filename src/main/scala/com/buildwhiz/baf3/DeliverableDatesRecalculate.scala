package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PhaseApi, ProcessApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import com.mongodb.client.model.UpdateOneModel
import org.bson.types.ObjectId
import org.bson.Document

import java.io.{ByteArrayOutputStream, PrintWriter}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DeliverableDatesRecalculate extends HttpServlet with HttpUtils with DateTimeUtils {

  private val margin = "|&nbsp;&nbsp;&nbsp;"

  private case class Globals(timezone: String, phaseStartDate: Long, activities: Seq[DynDoc],
      deliverables: Seq[DynDoc], constraints: Seq[DynDoc], deliverablesByOid: Map[ObjectId, DynDoc],
      constraintsByOwnerOid: Map[ObjectId, Seq[DynDoc]], procurementsByOid: Map[ObjectId, DynDoc],
      keyDataByOid: Map[ObjectId, DynDoc], respond: String => Unit,
      bulkWriteBuffer: mutable.Buffer[UpdateOneModel[Document]])

  private def msToDate(ms: Long, timezone: String): String = {
    dateString(ms, timezone)
  }

  private def dName(deliverable: DynDoc) = {
    val tun = deliverable.get[Int]("takt_unit_no") match {
      case None => -1
      case Some(t) => t
    }
    s"${deliverable.name[String]}[$tun] (${deliverable._id[ObjectId]})"
  }

  private def startDate(deliverable: DynDoc, level: Int, verbose: Boolean, g: Globals): Long = {
    if (verbose) {
      g.respond(margin * level +
          s"StartDate ${dName(deliverable)}<br/>")
    }
    if (g.constraintsByOwnerOid.contains(deliverable._id[ObjectId])) {
      val constraints = g.constraintsByOwnerOid(deliverable._id[ObjectId])
      val constraintEndDates: Seq[Long] = constraints.map(constraint => {
        val constraintDelay = constraint.getOrElse[Int]("delay", 0)
        val constraintOid = constraint.constraint_id[ObjectId]
        constraint.`type`[String] match {
          case "Document" | "Work" | "Milestone" | "Submittal" =>
            if (g.deliverablesByOid.contains(constraintOid)) {
              val constraintDeliverable = g.deliverablesByOid(constraintOid)
              val deliverableDate = endDate(constraintDeliverable, level + 1, verbose, g)
              //if (verbose)
              //  respond(margin * (level + 1) +
              //    s"DELIVERABLE: ${constraintDeliverable.name[String]} = ${msToDate(deliverableDate)}<br/>")
              addWeekdays(deliverableDate, constraintDelay, g.timezone)
            } else {
              g.respond("""<font color="red">""" + margin * (level + 1) +
                  s"ERROR: MISSING constraint-deliverable: $constraintOid</font><br/>")
              -1
            }
          case "Material" | "Labor" | "Equipment" =>
            if (g.procurementsByOid.contains(constraintOid)) {
              val procurementRecord = g.procurementsByOid(constraintOid)
              val procurementDate = procurementRecord.get[Int]("duration") match {
                case Some(d) => addWeekdays(g.phaseStartDate, d, g.timezone) //addDaysToDate(g.phaseStartDate, d)
                case None => g.phaseStartDate
              }
              if (verbose)
                g.respond(margin * (level + 1) +
                  s"PROCUREMENT End-Date: ${procurementRecord.name[String]} ($constraintOid) = ${msToDate(procurementDate, g.timezone)}<br/>")
              addWeekdays(procurementDate, constraintDelay, g.timezone)
            } else {
              g.respond("""<font color="red">""" + margin * (level + 1) +
                  s"ERROR: MISSING procurement: $constraintOid</font><br/>")
              -1
            }
          case "Data" =>
            if (g.keyDataByOid.contains(constraintOid)) {
              val keyDataRecord = g.keyDataByOid(constraintOid)
              val keyDataDate = keyDataRecord.get[Int]("duration") match {
                case Some(d) => addWeekdays(g.phaseStartDate, d, g.timezone) //addDaysToDate(g.phaseStartDate, d)
                case None => g.phaseStartDate
              }
              if (verbose) {
                g.respond(margin * (level + 1) +
                  s"DATA End-Date: ${keyDataRecord.name[String]} ($constraintOid) = ${msToDate(keyDataDate, g.timezone)}<br/>")
              }
              addWeekdays(keyDataDate, constraintDelay, g.timezone)
            } else {
              g.respond("""<font color="red">""" + margin * (level + 1) +
                  s"ERROR: MISSING data: $constraintOid</font><br/>")
              -1
            }
        }
      }).filter(_ != -1)
      val dateStart = if (constraintEndDates.nonEmpty) {
        constraintEndDates.max
      } else {
        g.phaseStartDate
      }
      if (verbose) {
        g.respond(margin * level +
          s"StartDate ${dName(deliverable)} = ${msToDate(dateStart, g.timezone)}<br/>")
      }
      dateStart
    } else {
      if (verbose) {
        g.respond("""<font color="blue">""" + margin * (level + 1) +
          s"MISSING constraints for deliverable: ${dName(deliverable)}</font><br/>")
        g.respond(margin * level +
          s"StartDate ${dName(deliverable)} = ${msToDate(g.phaseStartDate, g.timezone)}<br/>")
      }
      g.phaseStartDate
    }
  }

  private def deliverableBypassed(d: DynDoc): Boolean = d.status[String] == "Deliverable-Bypassed"

  private def endDate(deliverable: DynDoc, level: Int, verbose: Boolean, g: Globals): Long = {
    if (verbose) {
      g.respond(margin * level + s"EndDate ${dName(deliverable)}<br/>")
    }
    def setDates(deliverable: DynDoc, startDate: Long, endDate: Long, optFloatDays: Option[Long]): Unit = {
      val baseValues: Document = if (deliverableBypassed(deliverable)) {
        Map("date_start_estimated" -> startDate, "date_end_estimated" -> endDate, "date_end_actual" -> endDate)
      } else {
        Map("date_start_estimated" -> startDate, "date_end_estimated" -> endDate)
      }
      val setterValues = optFloatDays match {
        case Some(floatDays) => baseValues.append("float_days", floatDays)
        case None => baseValues
      }
      g.bulkWriteBuffer.append(new UpdateOneModel(new Document("_id", deliverable._id[ObjectId]),
        new Document($set, setterValues)))
    }
    val dateEnd = deliverable.get[Long]("end$date") match {
      case None =>
        val dt = (deliverable.get[Long]("date_end_actual"), deliverable.get[Long]("commit_date"),
            deliverableBypassed(deliverable)) match {
          case (Some(dateEndActual), _, false) =>
            dateEndActual
          case (_, optCommitDate, isBypassed) =>
            val estimatedStartDate = startDate(deliverable, level + 1, verbose, g)
            val duration = if (isBypassed) {
              0
            } else {
              deliverable.duration[Int]
            }
            val cumulativeEndDate = addWeekdays(estimatedStartDate, duration, g.timezone)
            val displayedEndDate = addWeekdays(cumulativeEndDate, -1, g.timezone)
            val newOptFloatDays: Option[Long] = optCommitDate match {
              case Some(commitDate) => Some(weekDaysBetween(estimatedStartDate, commitDate, g.timezone) -
                deliverable.duration[Int])
              case None => None
            }
            (deliverable.get[Long]("date_start_estimated"), deliverable.get[Long]("date_end_estimated"),
                deliverable.get[Long]("float_days")) match {
              case (Some(existingStartDate), Some(existingEndDate), optExistingFloatDays) =>
                if (existingStartDate == estimatedStartDate && existingEndDate == displayedEndDate &&
                    optExistingFloatDays == newOptFloatDays) {
                  if (verbose) {
                    g.respond(
                      """<font color="green">""" + margin * (level + 1) +
                        s"SKIPPING ${dName(deliverable)}</font><br/>")
                  }
                } else {
                  if (verbose) {
                    g.respond(
                      """<font color="green">""" + margin * (level + 1) +
                        s"UPDATING ${dName(deliverable)}</font><br/>")
                  }
                  setDates(deliverable, estimatedStartDate, displayedEndDate, newOptFloatDays)
                }
              case _ =>
                if (verbose) {
                  g.respond(
                    """<font color="green">""" + margin * (level + 1) +
                      s"INITIALIZING ${dName(deliverable)}</font><br/>")
                }
                setDates(deliverable, estimatedStartDate, displayedEndDate, newOptFloatDays)
            }
            optCommitDate match {
              case Some(commitDate) => commitDate
              case None => cumulativeEndDate
            }
        }
        deliverable.end$date = dt
        dt
      case Some(dt) => dt
    }
    if (verbose) {
      g.respond(margin * level + s"EndDate ${dName(deliverable)} = " +
          s"${msToDate(addWeekdays(dateEnd, -1, g.timezone), g.timezone)}<br/>")
    }
    dateEnd
  }

  private def traverseAllTrees(verbose: Boolean, g: Globals, request: HttpServletRequest): Unit = {
    val constraintsByConstraintOid = g.constraints.groupBy(_.constraint_id[ObjectId])
    val endDeliverables = g.deliverables.filter(d => !constraintsByConstraintOid.contains(d._id[ObjectId]))
    if (verbose) {
      g.respond(s"${endDeliverables.length}" +
          s"""End-Deliverables: ${endDeliverables.map(_.name[String]).mkString(", ")}<br/><br/>""")
    }
    for (endDeliverable <- endDeliverables) {
      endDate(endDeliverable, 0, verbose, g)
      if (verbose) {
        g.respond("<br/>")
      }
    }
    if (g.bulkWriteBuffer.nonEmpty) {
      BWLogger.log(getClass.getName, request.getMethod,
          s"traverseAllTrees() - MongoDB Bulk-Write: will attempt ${g.bulkWriteBuffer.length} updates", request)
      val bulkWriteResult = BWMongoDB3.deliverables.bulkWrite(g.bulkWriteBuffer.asJava)
      if (bulkWriteResult.getModifiedCount == 0) {
        BWLogger.log(getClass.getName, request.getMethod, "ERROR: MongoDB Bulk-Write FAILED", request)
        g.respond("""<font color="red">ERROR: MongoDB Bulk-Write FAILED<font/><br/>""")
      } else {
        BWLogger.log(getClass.getName, request.getMethod,
          s"traverseAllTrees() - MongoDB Bulk-Write: updated ${bulkWriteResult.getModifiedCount} records", request)
      }
    } else {
      BWLogger.log(getClass.getName, request.getMethod,
        "traverseAllTrees() - MongoDB Bulk-Write: NO updates needed", request)
    }
  }

  private def setKeyDataDates(globals: Globals, request: HttpServletRequest): Unit = {
    val bulkWriteBuffer = mutable.Buffer[UpdateOneModel[Document]]()
    for (kd <- globals.keyDataByOid.values) {
      val duration = kd.get[Int]("duration") match {
        case Some(d) => d
        case None => 7
      }
      val endDate = addWeekdays(globals.phaseStartDate, duration, globals.timezone)
      val displayEndDate = addWeekdays(endDate, -1, globals.timezone)
      val existingEndDate = kd.get[Long]("date_end_estimated") match {
        case Some(dee) => dee
        case None => -1
      }
      if (displayEndDate != existingEndDate) {
        bulkWriteBuffer.append(new UpdateOneModel(new Document("_id", kd._id[ObjectId]),
          new Document($set, new Document("date_end_estimated", displayEndDate))))
      }
    }
    if (bulkWriteBuffer.nonEmpty) {
      BWLogger.log(getClass.getName, request.getMethod,
        s"setKeyDataDates() - MongoDB Bulk-Write: will attempt ${bulkWriteBuffer.length} updates", request)
      val bulkWriteResult = BWMongoDB3.key_data.bulkWrite(bulkWriteBuffer.asJava)
      if (bulkWriteResult.getModifiedCount == 0) {
        BWLogger.log(getClass.getName, request.getMethod, "ERROR: MongoDB Bulk-Write FAILED", request)
        globals.respond("""<font color="red">ERROR: MongoDB Bulk-Write FAILED<font/><br/>""")
      } else {
        BWLogger.log(getClass.getName, request.getMethod,
          s"setKeyDataDates() - MongoDB Bulk-Write: updated ${bulkWriteResult.getModifiedCount} records", request)
      }
    } else {
      BWLogger.log(getClass.getName, request.getMethod,
        "setKeyDataDates() - MongoDB Bulk-Write: NO updates needed", request)
    }
  }

  private def handleDeliverables(respond: String => Unit, request: HttpServletRequest, response: HttpServletResponse,
      verbose: Boolean): Long = {
    val t0 = System.currentTimeMillis
    val parameters = getParameterMap(request)
    val (thePhase: DynDoc, theProcess: DynDoc) = (parameters.get("phase_id"), parameters.get("process_id")) match {
      case (Some(phId), None) =>
        val phase = PhaseApi.phaseById(new ObjectId(phId))
        val process = PhaseApi.allProcesses(phase).head
        (phase, process)
      case (None, Some(procId)) =>
        val process = ProcessApi.processById(new ObjectId(procId))
        val phase = ProcessApi.parentPhase(process._id[ObjectId])
        (phase, process)
      case _ => throw new IllegalArgumentException(s"Incomplete arguments")
    }
    val timezone = PhaseApi.timeZone(thePhase)
    val activities = ProcessApi.allActivities(Right(theProcess))
    val deliverables = DeliverableApi.deliverablesByActivityOids(activities.map(_._id[ObjectId]))
    val deliverableOids = deliverables.map(_._id[ObjectId])
    val deliverablesByOid: Map[ObjectId, DynDoc] = deliverableOids.zip(deliverables).toMap
    val constraints: Seq[DynDoc] = BWMongoDB3.constraints.find(Map("owner_deliverable_id" -> Map($in -> deliverableOids)))
    val constraintsByOwnerOid: Map[ObjectId, Seq[DynDoc]] = constraints.groupBy(_.owner_deliverable_id[ObjectId])
    val procurementsByOid: Map[ObjectId, DynDoc] = {
      val procurements: Seq[DynDoc] =
        BWMongoDB3.procurements.find(Map("activity_id" -> Map($in -> activities.map(_._id[ObjectId]))))
      procurements.map(_._id[ObjectId]).zip(procurements).toMap
    }
    val keyDataByOid: Map[ObjectId, DynDoc] = {
      val project = PhaseApi.parentProject(thePhase._id[ObjectId])
      val keyData: Seq[DynDoc] = BWMongoDB3.key_data.find(Map("project_id" -> project._id[ObjectId]))
      keyData.map(_._id[ObjectId]).zip(keyData).toMap
    }
    val optStartDate: Option[Long] = if (theProcess.`type`[String] == "Template") {
      Some(System.currentTimeMillis)
    } else {
      def dse(d: DynDoc): Option[Long] = d.get[Document]("timestamps").
          flatMap(doc => doc.y.get[Long]("date_start_estimated"))
      (dse(theProcess), dse(thePhase)) match {
        case (procStartDate: Some[Long], _) => procStartDate
        case (_, phaseStartDate: Some[Long]) => phaseStartDate
        case _ => None
      }
    }
    if (verbose) {
      respond("<html><br/><tt>")
      optStartDate match {
        case Some(dse) => respond(s"Phase 'date_start_estimated': ${msToDate(dse, timezone)}<br/>")
        case None => respond("WARNING: phase/process has no \'date_start_estimated\'")
      }
      respond(s"Deliverables: ${deliverables.length}, Constraints: ${constraints.length}<br/>")
      respond(s"Procurements: ${procurementsByOid.size}, KeyData: ${keyDataByOid.size} (project)<br/>")
      val (withDate, withoutDate) = deliverables.partition(_.has("date_end_estimated"))
      respond(s"With 'date_end_estimated': ${withDate.length}, Without 'date_end_estimated': ${withoutDate.length}<br/>")
      respond("<br/>")
    }
    optStartDate match {
      case Some(phaseStartDate) =>
        val globals = Globals(timezone, phaseStartDate, activities, deliverables, constraints, deliverablesByOid,
          constraintsByOwnerOid, procurementsByOid, keyDataByOid, respond, mutable.Buffer[UpdateOneModel[Document]]())
        setKeyDataDates(globals, request)
        traverseAllTrees(verbose, globals, request)
      case None =>
        BWLogger.log(getClass.getName, request.getMethod, "WARN: 'date_start_estimated' undefined. Dates NOT calculated", request)
    }
    response.setStatus(HttpServletResponse.SC_OK)
    val delay = System.currentTimeMillis - t0
    if (verbose) {
      respond(s"time: $delay ms<br/>")
      respond("</tt></html>")
    }
    delay
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val writer = response.getWriter
    try {
      response.setContentType("text/html")
      val parameters = getParameterMap(request)
      val verbose = parameters.get("verbose") match {
        case None => true
        case Some(v) => v.toBoolean
      }
      val delay = handleDeliverables(msg => writer.print(msg), request, response, verbose)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace(writer)
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val  printByteStream = new ByteArrayOutputStream()
      val writer = new PrintWriter(printByteStream)
      val delay = handleDeliverables(msg => writer.print(msg), request, response, verbose = false)
      writer.flush()
      val cleanMessages = printByteStream.toString.replaceAll("&[^;]+;", "").replaceAll("<br/>", ", ").
          replaceAll("<[^>]+>", "").trim()
      val messages = if (cleanMessages.contains("ERROR")) {
        cleanMessages
      } else {
        ""
      }
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms) messages: $cleanMessages", request)
      response.setContentType("application/json")
      response.getWriter.print(successJson(fields = Map("messages" -> messages, "delay" -> delay)))
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace(response.getWriter)
      throw t
    }
  }

}

