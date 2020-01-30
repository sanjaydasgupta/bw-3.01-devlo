package com.buildwhiz.utils

import com.buildwhiz.baf2.{DashboardEntries, PersonApi, TaskList}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.slack.SlackApi
import org.bson.Document
import org.bson.types.ObjectId

import scala.util.parsing.combinator.RegexParsers

object CommandLineProcessor extends DateTimeUtils {

  type ParserResult = Either[String, Seq[DynDoc]]

  def process(command: String, user: DynDoc): ParserResult = {
    commandLineParser.cliProcessor(command, user)
  }

  private def help(user: DynDoc): ParserResult = {
    Left("""Commands:
      |+  dash[board]
      |+  list {active users|documents|projects|tasks}
      |+  slack {invite|status} first-name [last-name]
      |+  who am I""".stripMargin)
  }

  private def listDocuments(user: DynDoc): ParserResult = {
    Left("You have no documents now")
  }

  private def listProjects(user: DynDoc): ParserResult = {
    Left("You have no projects now")
  }

  private def listTasks(user: DynDoc): ParserResult = {
    val assignments = TaskList.uniqueAssignments(user)
    val rows = assignments.map(assignment => {
      val id = assignment.activity_id[ObjectId]
      val project = assignment.project_name[String]
      val phase = assignment.phase_name[String]
      val process = assignment.process_name[String]
      val name = assignment.activity_name[String]
      val end = assignment.end_datetime[String].split(" ").head
      val start = assignment.start_datetime[String].split(" ").head
      (s"$name (END: $end, START: $start)", id.toString)
    })
    Right(SlackApi.createMultipleChoiceMessage(rows, "task-list"))
  }

  private def activeUsers(user: DynDoc): ParserResult = {
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
    Left(rows.mkString("Last presence in past 1 hour\n", "", s"Total ${rows.length} users."))
  }

  private def dashboard(user: DynDoc): ParserResult = {
    val entries: Seq[DynDoc] = DashboardEntries.dashboardEntries(user)
    val rows = entries.map(entry => {
      val project = entry.project_name[String]
      val phase = entry.phase_name[String]
      val status = entry.display_status[String]
      val tasksOverdueDetail: DynDoc = entry.tasks_overdue[Document]
      val tasksOverdue = tasksOverdueDetail.value[String]
      s"Project: $project, Phase: $phase, Status: $status, Tasks-Overdue: $tasksOverdue"
    })
    Left(rows.mkString("\n"))
  }

  private def whoAmI(user: DynDoc): ParserResult = {
    Left(s"You are ${PersonApi.fullName(user)}")
  }

  private def slackManage(op: String, names: List[String]): DynDoc => ParserResult = {
    if (!op.toLowerCase.matches("invite|status"))
      return _ => Left(s"Unknown operation: $op")
    val persons: Seq[DynDoc] = names match {
      case firstName +: Nil => BWMongoDB3.persons.find(Map("first_name" -> Map($regex -> s"(?i)$firstName")))
      case firstName +: lastName +: Nil => BWMongoDB3.persons.find(Map("first_name" -> Map($regex -> s"(?i)$firstName"),
          "last_name" -> Map($regex -> s"(?i)$lastName")))
      case _ => Nil
    }
    persons.length match {
      case 0 => _ => Left(s"""Unknown user name: ${names.mkString(" ")}""")
      case 1 => op.toLowerCase match {
        case "invite" => dd => Left(SlackApi.invite(persons.head)(dd))
        case "status" => dd => Left(SlackApi.status(persons.head)(dd))
      }
      case _ => _ => Left(s"""Ambiguous user name: ${names.mkString(" ")}""")
    }
  }

  private object commandLineParser extends RegexParsers {

    // Type for CLI processor parsers ...
    // ... a function that takes a person object as input and returns the result string
    private type CLIP = Parser[DynDoc => ParserResult]

    // Help command ...
    private lazy val helpParser: CLIP = "(?i)help" ^^ {_ => help}

    // Slack membership command ...
    private lazy val slackManageParser: CLIP = "slack" ~> ("invite" | "status") ~ rep1("\\S+".r) ^^
      {parseResult => slackManage(parseResult._1, parseResult._2)}

    // List entities command ...
    private lazy val entityNamesParser: Parser[String] =
      ("(?i)active".r ~ "(?i)users?".r) ^^ {_ => "active-users"} |
      "(?i)documents?".r ^^ {_ => "documents"} |
      "(?i)projects?".r ^^ {_ => "projects"} |
      "(?i)tasks?".r ^^ {_ => "tasks"}

    private lazy val listEntitiesParser: CLIP = "(?i)list|show|display|query".r ~> entityNamesParser ^^ {
      case "active-users" => activeUsers
      case "documents" => listDocuments
      case "projects" => listProjects
      case "tasks" => listTasks
    }

    // Dashboard command ...
    private lazy val dashboardParser: CLIP = "(?i)dash(?:board)?".r ^^ {_ => dashboard}

    // Who am I command ...
    private lazy val whoAmIParser: CLIP = "(?i)who".r ~ "(?i)am".r ~ "[iI]".r ^^ {_ => whoAmI}

    private lazy val none: CLIP = ".*".r ^^ {_ => help}

    private lazy val allCommands: CLIP =
        dashboardParser | helpParser | listEntitiesParser | slackManageParser | whoAmIParser | none

    def cliProcessor(command: String, user: DynDoc): ParserResult = {
      parseAll(allCommands, command) match {
        case Success(result, _) => result(user)
        case NoSuccess(result, _) => Left(result)
      }
    }

    def testParser(command: String): ParserResult = {
      val sanjay: DynDoc = BWMongoDB3.persons.find(Map("last_name" -> "Dasgupta")).head
      parseAll(allCommands, command) match {
        case Success(result, _) => result(sanjay)
        case NoSuccess(result, _) => Left(result)
      }
    }

  }

  def main(args: Array[String]): Unit = {
    println(commandLineParser.testParser("Who am i"))
    println(commandLineParser.testParser("DashBoard"))
    println(commandLineParser.testParser("query Active UsErS"))
    println(commandLineParser.testParser("list documents"))
    println(commandLineParser.testParser("list projects"))
    //println(commandLineParser.testParser("list tasks"))
    println(commandLineParser.testParser("slack status caroline"))
    //println(commandLineParser.testParser("list phases"))
  }

}
