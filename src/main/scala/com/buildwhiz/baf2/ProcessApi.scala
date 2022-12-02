package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity

import scala.io.Source
import scala.jdk.CollectionConverters._
import org.apache.http.impl.client.HttpClients

import javax.servlet.http.HttpServletRequest
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

import java.util.Calendar

object ProcessApi {

  def listProcesses(): Seq[DynDoc] = {
    BWMongoDB3.processes.find()
  }

  def processesByIds(processOids: Seq[ObjectId]): Seq[DynDoc] =
    BWMongoDB3.processes.find(Map("_id" -> Map($in -> processOids)))

  def processById(processOid: ObjectId): DynDoc = BWMongoDB3.processes.find(Map("_id" -> processOid)).head

  def exists(processOid: ObjectId): Boolean = BWMongoDB3.processes.find(Map("_id" -> processOid)).nonEmpty

  def allActivityOids(process: DynDoc): Seq[ObjectId] = process.get[Many[ObjectId]]("activity_ids") match {
    case Some(activityOids) => activityOids
    case None => Seq.empty
  }

  def allActivities(processIn: Either[ObjectId, DynDoc], filter: Map[String, Any] = Map.empty): Seq[DynDoc] = {
    val process = processIn match {
      case Right(pr) => pr
      case Left(oid) => processById(oid)
    }
    val activityOids = allActivityOids(process)
    ActivityApi.activitiesByIds(activityOids, filter)
  }

  def delete(process: DynDoc, request: HttpServletRequest): Unit = {
    val processOid = process._id[ObjectId]
    if (isActive(process))
      throw new IllegalArgumentException(s"Process '${process.name[String]}' is still active")

    BWMongoDB3.activity_assignments.deleteMany(Map("process_id" -> processOid))

    val processDeleteResult = BWMongoDB3.processes.deleteOne(Map("_id" -> processOid))
    if (processDeleteResult.getDeletedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $processDeleteResult")

    val phaseUpdateResult = BWMongoDB3.phases.updateOne(Map("process_ids" -> processOid),
      Map("$pull" -> Map("process_ids" -> processOid)))

    val activityOids: Seq[ObjectId] = allActivities(Right(process)).map(_._id[ObjectId])
    val activityDeleteCount = if (activityOids.nonEmpty)
      BWMongoDB3.tasks.deleteMany(Map("_id" -> Map("$in" -> activityOids))).getDeletedCount
    else
      0
    val deliverableDeleteCount = if (activityOids.nonEmpty)
      BWMongoDB3.deliverables.deleteMany(Map("activity_id" -> Map("$in" -> activityOids))).getDeletedCount
    else
      0

    val message = s"Deleted process '${process.name[String]}' (${process._id[ObjectId]}). " +
      s"Also updated ${phaseUpdateResult.getModifiedCount} phase records, " +
      s"and deleted $activityDeleteCount activities, $deliverableDeleteCount deliverables"
    BWLogger.audit(getClass.getName, request.getMethod, message, request)
  }

  def parentPhase(processOid: ObjectId): DynDoc = {
    BWMongoDB3.phases.find(Map("process_ids" -> processOid)).head
  }

  def canDelete(process: DynDoc): Boolean = !isActive(process)

  def canLaunch(process: DynDoc, parentPhase: DynDoc, user: DynDoc): Boolean = {
    val allActivitiesAssigned = ProcessApi.allActivityOids(process).forall(activityOid => {
      val assignments = ActivityApi.teamAssignment.list(activityOid)
      assignments.forall(_.has("person_id"))
    })
    allActivitiesAssigned && PhaseApi.canManage(user._id[ObjectId], parentPhase) &&
        process.status[String] == "defined"
  }

  def isActive(process: DynDoc): Boolean = {
    //val processName = process.name[String]
    if (process.has("process_instance_id")) {
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      val query = rts.createProcessInstanceQuery()
      val instance = query.processInstanceId(process.process_instance_id[String]).active().singleResult()
      val found = instance != null
      //val message = s"Process '$processName' has 'process_instance_id' field, Active instances found: $found"
      //BWLogger.log(getClass.getName, "isActive", message)
      found
    } else {
      //val message = s"Process '$processName' has no 'process_instance_id'"
      //BWLogger.log(getClass.getName, "isActive", message)
      false
    }
  }

  def isHealthy(process: DynDoc): Boolean = (process.status[String] == "running") == isActive(process)

  def hasRole(personOid: ObjectId, process: DynDoc): Boolean = {
    isAdmin(personOid, process) || allActivities(Right(process)).exists(activity => ActivityApi.hasRole(personOid, activity))
  }

  def isAdmin(personOid: ObjectId, process: DynDoc): Boolean =
    process.admin_person_id[ObjectId] == personOid

  def isManager(personOid: ObjectId, process: DynDoc): Boolean = process.assigned_roles[Many[Document]].
    exists(ar => ar.person_id[ObjectId] == personOid &&
      ar.role_name[String].matches("(?i)(?:project-|phase-|process-)?manager"))

  def canManage(personOid: ObjectId, process: DynDoc): Boolean =
      isManager(personOid, process) || isAdmin(personOid, process) ||
      PhaseApi.canManage(personOid, parentPhase(process._id[ObjectId]))

  def isZombie(process: DynDoc): Boolean = process.has("is_zombie") && process.is_zombie[Boolean]

  def displayStatus(process: DynDoc): String = {
    if (isActive(process)) {
      "active"
    } else {
      val timestamps: DynDoc = process.timestamps[Document]
      if (timestamps.has("end"))
        "ended"
      else if (timestamps.has("start"))
        "error"
      else
        "dormant"
    }
  }

  def managers(process: DynDoc): Seq[ObjectId] = {
    val phase = parentPhase(process._id[ObjectId])
    val phaseManagers = PhaseApi.managers(Right(phase))
    val processManagers = process.assigned_roles[Many[Document]].
      filter(_.role_name[String].matches(".*(?i)manager")).map(_.person_id[ObjectId])
    (phaseManagers ++ processManagers).distinct
  }

  def timeZone(process: DynDoc): String = {
    if (process.has("tz")) {
      process.tz[String]
    } else {
      val managerOids = managers(process)
      PersonApi.personsByIds(managerOids).map(_.tz[String]).groupBy(t => t).
        map(p => (p._1, p._2.length)).reduce((a, b) => if (a._2 > b._2) a else b)._1
    }
  }

  def validateNewName(newName: String, parentPhaseOid: ObjectId): Boolean = {
    val nameLength = newName.length
    if (newName.trim.length != nameLength)
      throw new IllegalArgumentException(s"Bad process name (has blank padding): '$newName'")
    if (nameLength > 150 || nameLength < 5)
      throw new IllegalArgumentException(s"Bad process name length: $nameLength (must be 5-150)")
    val siblingProcessOids: Seq[ObjectId] = PhaseApi.allProcessOids(PhaseApi.phaseById(parentPhaseOid))
    val count = BWMongoDB3.processes.countDocuments(Map("name" -> newName, "_id" -> Map($in -> siblingProcessOids)))
    if (count > 0)
      throw new IllegalArgumentException(s"Process named '$newName' already exists")
    true
  }

  def checkProcessSchedules(scheduleMs: Long): Unit = {
    def createNewTransientProcess(schedule: DynDoc): Unit = {
      def createRequest(schedule: DynDoc): HttpPost = {
        val request = new HttpPost("http://localhost:3000/ProcessClone4")
        val parameterEntity = Document.parse(schedule.asDoc.toJson)
        def oid2string(oid: Any): Any = oid.toString
        def oids2stringSeq(oids: Any): Any = oids.asInstanceOf[Many[ObjectId]].map(oid2string)
        def s2s(s: Any): Any = s
        def ms2string(ms: Any): Any = {
          val calendar = Calendar.getInstance()
          calendar.setTimeInMillis(ms.asInstanceOf[Long])
          "%02d/%02d/%4d".format(calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
              calendar.get(Calendar.YEAR))
        }
        def tp2s(tp: Any): Seq[Any] = {
          tp.asInstanceOf[Many[Document]].map(p => {
            val newDoc = new Document("name", p.name[String]).append("type", p.`type`[String]).
                append("runtime", p.runtime[Boolean]).append("value",
            p.`type`[String] match {
              case "Text" => p.value[Any]
              case "Boolean" => p.value[Any] match {
                case b: Boolean => Map(true -> "1", false -> "0")(b)
                case x => x
              }
              case "List" => p.value[Any]
              case "Number" => p.value[Any] match {
                case nbr: BigDecimal => nbr.toDouble.toString
                case nbr: BigInt => nbr.toDouble.toString
                case nbr: Number => nbr.doubleValue().toString
                case nbr: Float => nbr.toString
                case nbr: Int => nbr.toString
                case nbr: Double => nbr.toString
                case nbr: Long => nbr.toString
                case x => x.toString
              }
              case "Date" => p.value[Any] match {
                case msl: Long => ms2string(msl)
                case msl: Int => ms2string(msl)
                case x => x.toString
              }
            })
            newDoc
          })
        }
        val requiredKeyInfos: Map[String, Any => Any] = Seq(
          ("project_id", oid2string _), ("phase_id", oid2string _), ("template_process_id", oid2string _),
          ("title", s2s _), ("priority", s2s _), ("zones", oids2stringSeq _), ("target_date", ms2string _),
          ("template_parameters", tp2s _)).toMap
        val newParmEntity: Document = parameterEntity.entrySet().asScala.
            filter(e => requiredKeyInfos.contains(e.getKey)).map(e => {
          val (key, value) = (e.getKey, e.getValue)
          val newValue = requiredKeyInfos(key)(value)
          //BWLogger.log(getClass.getName, "LOCAL",
          //    s"AUDIT-???-checkProcessSchedules KEY: $key, VALUE: $value NEW-VALUE: $newValue")
          (key, newValue)
        }).toMap
        if (!newParmEntity.containsKey("title")) {
          newParmEntity.put("title", parameterEntity.get("name"))
        }
        if (!newParmEntity.containsKey("target_date")) {
          val targetDuration = if (parameterEntity.containsKey("target_duration")) {
            parameterEntity.get("target_duration").asInstanceOf[Int]
          } else {
            5
          }
          val cal = Calendar.getInstance()
          cal.add(Calendar.DAY_OF_MONTH, targetDuration)
          val targetDate = "%02d/%02d/%4d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
              cal.get(Calendar.YEAR))
          newParmEntity.put("target_date", targetDate)
        }
        val parameterEntityJson = newParmEntity.toJson
        BWLogger.log(getClass.getName, "LOCAL", s"AUDIT-???-checkProcessSchedules CALL-parameters: $parameterEntityJson")
        request.setEntity(new StringEntity(parameterEntityJson))
        request
      }
      val t0 = System.currentTimeMillis()
      val nodeResponse = HttpClients.createDefault().execute(createRequest(schedule))
      val delay = System.currentTimeMillis() - t0
      val nodeEntity = nodeResponse.getEntity
      val nodeEntityString = Source.fromInputStream(nodeEntity.getContent).getLines().mkString("\n")
      BWLogger.log(getClass.getName, "LOCAL", s"AUDIT-???-checkProcessSchedules node-response: $nodeEntityString")
      val nodeEntityDoc: DynDoc = Document.parse(nodeEntityString)
      if (nodeEntityDoc.ok[Int] == 1) {
        val calendar = Calendar.getInstance()
        calendar.setTimeInMillis(scheduleMs)
        val runNext: Long = schedule.frequency[String] match {
          case "Quarter-Hourly" => calendar.add(Calendar.MINUTE, 15); calendar.getTimeInMillis
          case "Half-Hourly" => calendar.add(Calendar.MINUTE, 30); calendar.getTimeInMillis
          case "Hourly" => calendar.add(Calendar.HOUR, 1); calendar.getTimeInMillis
          case "Twice-Daily" => calendar.add(Calendar.HOUR, 12); calendar.getTimeInMillis
          case "Daily" => calendar.add(Calendar.DAY_OF_MONTH, 1); calendar.getTimeInMillis
          case "Weekly" => calendar.add(Calendar.DAY_OF_MONTH, 7); calendar.getTimeInMillis
          case "Monthly" => calendar.add(Calendar.MONTH, 1); calendar.getTimeInMillis
          case "Quarterly" => calendar.add(Calendar.MONTH, 3); calendar.getTimeInMillis
          case "Half-Yearly" => calendar.add(Calendar.MONTH, 6); calendar.getTimeInMillis
          case "Annually" => calendar.add(Calendar.YEAR, 1); calendar.getTimeInMillis
        }
        BWMongoDB3.process_schedules.updateOne(Map("_id" -> schedule._id[ObjectId]),
            Map($set -> Map("timestamps.run_next" -> runNext, "timestamps.last_success" -> scheduleMs,
            "timestamps.last_message" -> nodeEntityDoc.message[String])))
        BWLogger.log(getClass.getName, "LOCAL", s"AUDIT-checkProcessSchedules(time: $delay): " +
          s"${schedule.name[String]} (${schedule._id[ObjectId]}) -> $runNext")
      } else {
        BWMongoDB3.process_schedules.updateOne(Map("_id" -> schedule._id[ObjectId]),
            Map($set -> Map("timestamps.run_next" -> scheduleMs, "timestamps.last_failure" -> scheduleMs,
            "timestamps.last_message" -> nodeEntityDoc.message[String])))
        BWLogger.log(getClass.getName, "LOCAL", s"ERROR-checkProcessSchedules(time: $delay): " +
          s"${schedule.name[String]} (${schedule._id[ObjectId]}) -> $scheduleMs")
      }
    }
    val readySchedules: Seq[DynDoc] = BWMongoDB3.process_schedules.
      find(Map("timestamps.run_next" -> Map($lte -> scheduleMs), "timestamps.end" -> Map($gte -> scheduleMs)))
    BWLogger.log(getClass.getName, "LOCAL", s"AUDIT-???-checkProcessSchedules ready-schedules: ${readySchedules.length}")
    for (schedule <- readySchedules) {
      try {
        createNewTransientProcess(schedule)
      } catch {
        case t: Throwable =>
          BWLogger.log(getClass.getName, "LOCAL", s"ERROR-checkProcessSchedules: " +
            s"${schedule.name[String]} (${schedule._id[ObjectId]}) -> ${t.getClass.getSimpleName}(${t.getMessage})")
      }
    }
  }

}