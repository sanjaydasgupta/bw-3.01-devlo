package com.buildwhiz.utils

import com.buildwhiz.baf2.{DashboardEntries, PersonApi, PhaseApi, PhaseInfo, ProjectApi, ProjectInfo, RfiList, TaskList}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.slack.SlackApi
import org.bson.Document
import org.bson.types.ObjectId

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.util.parsing.combinator.RegexParsers

object CommandLineProcessor extends DateTimeUtils {

  type ParserResult = Option[String]

  def process(command: String, user: DynDoc, postData: DynDoc): ParserResult = {
    commandLineParser.cliProcessor(command, user, postData)
  }

  private object helpers {

    def slackManage(op: String, names: List[String]): (DynDoc, DynDoc) => ParserResult = {
      if (!op.toLowerCase.matches("invite|status"))
        return (_, _) => Some(s"Unknown operation: $op")
      val persons: Seq[DynDoc] = names match {
        case firstName +: Nil => BWMongoDB3.persons.find(Map("first_name" -> Map($regex -> s"(?i)$firstName")))
        case firstName +: lastName +: Nil => BWMongoDB3.persons.find(Map("first_name" -> Map($regex -> s"(?i)$firstName"),
            "last_name" -> Map($regex -> s"(?i)$lastName")))
        case _ => Nil
      }
      persons.length match {
        case 0 => (_, _) => Some(s"""Unknown user name: ${names.mkString(" ")}""")
        case 1 => op.toLowerCase match {
          case "invite" => (dd, _) => Some(SlackApi.invite(persons.head)(dd))
          case "status" => (dd, _) => Some(SlackApi.status(persons.head)(dd))
        }
        case _ => (_, _) => Some(s"""Ambiguous user name: ${names.mkString(" ")}""")
      }
    }

    def whoAmI(user: DynDoc, postData: DynDoc): ParserResult = {
        Some(s"You are ${PersonApi.fullName(user)}")
    }

    def describePhase(phaseId: String): (DynDoc, DynDoc) => ParserResult = {
      (user: DynDoc, postData: DynDoc) => {
        val phaseOid = new ObjectId(phaseId)
        val phase = PhaseApi.phaseById(phaseOid)
        val phaseInfo: DynDoc = Document.parse(PhaseInfo.phase2json(phase, user))
        val name = phaseInfo.name[Document].y.value[String]
        val status = phaseInfo.status[Document].y.value[String]
        val description = phaseInfo.description[Document].y.value[String]
        val processes: Seq[DynDoc] = phaseInfo.process_info[Many[Document]]
        val processInfos = processes.map(process => s"${process.name[String]} (${process.status[String]})").mkString(", ")
        Some(s"Name: $name\nStatus: $status\nDescription: $description\nProcesses: $processInfos")
      }
    }

    def describeProject(projectId: String): (DynDoc, DynDoc) => ParserResult = {
      (user: DynDoc, postData: DynDoc) => {
        val projectOid = new ObjectId(projectId)
        val project = ProjectApi.projectById(projectOid)
        val projectInfo: DynDoc = Document.parse(ProjectInfo.project2json(project, user))
        val name = projectInfo.name[Document].y.value[String]
        val status = projectInfo.status[Document].y.value[String]
        val description = projectInfo.description[Document].y.value[String]
        val phases: Seq[DynDoc] = projectInfo.phase_info[Document].y.value[Many[Document]]
        val phaseInfos = phases.map(phase => s"${phase.name[String]} (${phase.status[String]})").mkString(", ")
        Some(s"Name: $name\nStatus: $status\nDescription: $description\nPhases: $phaseInfos")
      }
    }

    def dashboard(user: DynDoc, postData: DynDoc): ParserResult = {
      val entries: Seq[DynDoc] = DashboardEntries.dashboardEntries(user)
      val messages = entries.map(entry => {
        val project = entry.project_name[String]
        val phase = entry.phase_name[String]
        val status = entry.display_status[String]
        val tasksOverdueDetail: DynDoc = entry.tasks_overdue[Document]
        val tasksOverdue = tasksOverdueDetail.value[String]
        s"""Project '$project', Phase: '$phase', Status: '$status', Tasks-Overdue: $tasksOverdue""".stripMargin
      })
      val allMessages = s"""You have ${messages.length} dashboard item(s). Item messages follow.
                           |(_open a *thread* to start an interaction_)""".stripMargin +: messages
      for (message <- allMessages) {
        Future {SlackApi.sendToChannel(Left(message), postData.channel[String], None)}.onComplete {
          case Failure(ex) =>
            BWLogger.log(getClass.getName, "dashboard()", s"ERROR: ${ex.getClass.getSimpleName}(${ex.getMessage})")
          case Success(_) =>
        }
      }
      None
    }

    def activeUsers(user: DynDoc, postData: DynDoc): ParserResult = {
      val msStart = System.currentTimeMillis() - (60L * 60L * 1000L)
      val logs: Seq[DynDoc] = BWMongoDB3.trace_log.find(Map("milliseconds" -> Map($gte -> msStart)))
      val namedLogs = logs.filter(_.variables[Document].has("u$nm"))
      val lastOccurrence: Seq[DynDoc] = namedLogs.groupBy(_.variables[Document].getString("u$nm")).
          map(_._2.maxBy(_.milliseconds[Long])).toSeq.sortBy(_.milliseconds[Long] * -1)
      val rows = lastOccurrence.map(occurrence => {
        val name = occurrence.variables[Document].getString("u$nm")
        val time = dateTimeString(occurrence.milliseconds[Long], Some(user.tz[String]))
        s"\t$time  $name\n"
      })
      Some(rows.mkString("Last presence in past 1 hour\n", "", s"Total ${rows.length} users."))
    }

    def listTasks(user: DynDoc, postData: DynDoc): ParserResult = {
      val assignments = TaskList.uniqueAssignments(user)
      val taskMessages = assignments.map(assignment => {
        val id = assignment.activity_id[ObjectId]
        val project = assignment.project_name[String]
        val phase = assignment.phase_name[String]
        val process = assignment.process_name[String]
        val name = assignment.activity_name[String]
        val end = assignment.end_datetime[String].split(" ").head
        val start = assignment.start_datetime[String].split(" ").head
        s"""*Task '$name' (end: $end, start: $start) id:$id*
           |process: '$process', phase: '$phase', project: '$project'""".stripMargin
      })
      val allMessages = s"""You have ${taskMessages.length} task(s). Task messages follow.
                           |(_open a *thread* to start an interaction_)""".stripMargin +: taskMessages
      for (message <- allMessages) {
        Future {SlackApi.sendToChannel(Left(message), postData.channel[String], None)}.onComplete {
          case Failure(ex) =>
            BWLogger.log(getClass.getName, "listTasks()", s"ERROR: ${ex.getClass.getSimpleName}(${ex.getMessage})")
          case Success(_) =>
        }
      }
      None
    }

    def listProjects(user: DynDoc, postData: DynDoc): ParserResult = {
      val projects = ProjectApi.projectsByUser(user._id[ObjectId])
      val messages = projects.map(project => s"Project-name: '${project.name[String]}', Status: '${project.status[String]}'")
      val allMessages = s"""You have ${projects.length} project(s). Project messages follow.
                           |(_open a *thread* to start an interaction_)""".stripMargin +: messages
      for (message <- allMessages) {
        Future {SlackApi.sendToChannel(Left(message), postData.channel[String], None)}.onComplete {
          case Failure(ex) =>
            BWLogger.log(getClass.getName, "listProjects()", s"ERROR: ${ex.getClass.getSimpleName}(${ex.getMessage})")
          case Success(_) =>
        }
      }
      None
    }

    def listPhases(user: DynDoc, postData: DynDoc): ParserResult = {
      val phases = PhaseApi.phasesByUser(user._id[ObjectId])
      val messages = phases.map(phase => s"Phase-name: '${phase.name[String]}', Status: '${phase.status[String]}'")
      val allMessages = s"""You have ${phases.length} phase(s). Phase messages follow.
                           |(_open a *thread* to start an interaction_)""".stripMargin +: messages
      for (message <- allMessages) {
        Future {SlackApi.sendToChannel(Left(message), postData.channel[String], None)}.onComplete {
          case Failure(ex) =>
            BWLogger.log(getClass.getName, "listProjects()", s"ERROR: ${ex.getClass.getSimpleName}(${ex.getMessage})")
          case Success(_) =>
        }
      }
      None
    }

    def listIssues(user: DynDoc, postData: DynDoc): ParserResult = {
      val issues: Seq[DynDoc] = RfiList.getRfiList(user, None, None, None, None, None)
      val fields = Seq("Subject", "Status", "Priority", "Question")
      val messages = issues.map(issue => fields.map(field => s"$field: ${issue.get[String](field.toLowerCase)}").mkString(", "))
      val allMessages = s"""You have ${issues.length} issue(s). Issue messages follow.
                           |(_open a *thread* to start an interaction_)""".stripMargin +: messages
      for (message <- allMessages) {
        Future {SlackApi.sendToChannel(Left(message), postData.channel[String], None)}.onComplete {
          case Failure(ex) =>
            BWLogger.log(getClass.getName, "listProjects()", s"ERROR: ${ex.getClass.getSimpleName}(${ex.getMessage})")
          case Success(_) =>
        }
      }
      None
    }

    def listDocuments(user: DynDoc, postData: DynDoc): ParserResult = {
      Some("You have no documents now")
    }

    def help(user: DynDoc, postData: DynDoc): ParserResult = {
      Some("""Commands:
        |+  dash[board]
        |+  list {active users|documents|projects|tasks}
        |+  slack {invite|status} first-name [last-name]
        |+  who am I""".stripMargin)
    }

  }

  private object commandLineParser extends RegexParsers {

    // Type for CLI processor parsers ...
    // ... a function that takes a person object as input and returns the result string
    private type CLIP = Parser[(DynDoc, DynDoc) => ParserResult]

    // Help command ...
    private lazy val helpParser: CLIP = "(?i)help" ^^ {_ => helpers.help}

    // Slack membership command ...
    private lazy val slackManageParser: CLIP = "slack" ~> ("invite" | "status") ~ rep1("\\S+".r) ^^
      {parseResult => helpers.slackManage(parseResult._1, parseResult._2)}

    // List entities command ...
    private lazy val entityNamesParser: Parser[String] =
      ("(?i)active".r ~ "(?i)users?".r) ^^ {_ => "active-users"} |
      "(?i)documents?".r ^^ {_ => "documents"} |
      "(?i)issues?".r ^^ {_ => "issues"} |
      "(?i)phases?".r ^^ {_ => "phases"} |
      "(?i)projects?".r ^^ {_ => "projects"} |
      "(?i)tasks?".r ^^ {_ => "tasks"}

    private lazy val listEntitiesParser: CLIP = "(?i)display|list|query|show".r ~> opt("(?i)my".r) ~> entityNamesParser ^^ {
      case "active-users" => helpers.activeUsers
      case "documents" => helpers.listDocuments
      case "issues" => helpers.listIssues
      case "phases" => helpers.listPhases
      case "projects" => helpers.listProjects
      case "tasks" => helpers.listTasks
      case other => (_, _) => Some(s"Unknown option '$other'")
    }

    // Describe entity command ...
    private lazy val id: Parser[String] = "(?i)[0-9a-f]{24}".r

    private lazy val describeEntityParser: CLIP = "(?i)describe|display|dump|show".r ~> entityNamesParser ~ id ^^ {
      case "projects" ~ id => helpers.describeProject(id)
      case "phases" ~ id => helpers.describePhase(id)
      case _ => (user, postData) => helpers.help(user, postData)
    }

    // Dashboard command ...
    private lazy val dashboardParser: CLIP = "(?i)dash(?:board)?".r ^^ {_ => helpers.dashboard}

    // Who am I command ...
    private lazy val whoAmIParser: CLIP = "(?i)who".r ~ "(?i)am".r ~ "[iI]".r ^^ {_ => helpers.whoAmI}

    private lazy val none: CLIP = ".*".r ^^ {_ => helpers.help}

    private lazy val allCommands: CLIP =
        dashboardParser | describeEntityParser | helpParser | listEntitiesParser | slackManageParser | whoAmIParser | none

    def cliProcessor(command: String, user: DynDoc, postData: DynDoc): ParserResult = {
      parseAll(allCommands, command) match {
        case Success(result, _) => result(user, postData)
        case NoSuccess(result, _) => Some(result)
      }
    }

    def testParser(command: String): ParserResult = {
      val sanjay: DynDoc = BWMongoDB3.persons.find(Map("last_name" -> "Dasgupta")).head
      parseAll(allCommands, command) match {
        case Success(result, _) => result(sanjay, null)
        case NoSuccess(result, _) => Some(result)
      }
    }

  }

  def main(args: Array[String]): Unit = {
    println(commandLineParser.testParser("Who am i"))
    println(commandLineParser.testParser("describe project 5caffdb93c364b1b6f270688"))
    println(commandLineParser.testParser("list projects"))
    //println(commandLineParser.testParser("DashBoard"))
    println(commandLineParser.testParser("query Active UsErS"))
    println(commandLineParser.testParser("list documents"))
    println(commandLineParser.testParser("list projects"))
    //println(commandLineParser.testParser("list tasks"))
    println(commandLineParser.testParser("slack status caroline"))
    //println(commandLineParser.testParser("list phases"))
  }

}
