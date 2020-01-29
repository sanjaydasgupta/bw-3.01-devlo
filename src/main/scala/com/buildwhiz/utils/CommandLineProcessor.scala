package com.buildwhiz.utils

import com.buildwhiz.baf2.{DashboardEntries, PersonApi, TaskList}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.slack.SlackApi
import org.bson.Document
import org.bson.types.ObjectId

import scala.util.parsing.combinator.RegexParsers

object CommandLineProcessor {

  def process(command: String, user: DynDoc): String = {
    commandLineParser.cliProcessor(command, user)
  }

  private def help(user: DynDoc): String = {
    """Commands:
      |+  dash[board]
      |+  list {projects|tasks|documents}
      |+  slack {invite|status} first-name [last-name]
      |+  who am I""".stripMargin
  }

  private def listDocuments(user: DynDoc): String = {
    "You have no documents now"
  }

  private def listProjects(user: DynDoc): String = {
    "You have no projects now"
  }

  private def listTasks(user: DynDoc): String = {
    val assignments = TaskList.uniqueAssignments(user)
    val rows = assignments.map(assignment => {
      val id = assignment.activity_id[ObjectId]
      val project = assignment.project_name[String]
      val phase = assignment.phase_name[String]
      val process = assignment.process_name[String]
      val name = assignment.activity_name[String]
      val end = assignment.end_datetime[String].split(" ").head
      val start = assignment.start_datetime[String].split(" ").head
      s"$name (END: $end, START: $start) ID: $project/$phase/$process($id)"
    })
    rows.mkString("\n")
  }

  private def dashboard(user: DynDoc): String = {
    val entries: Seq[DynDoc] = DashboardEntries.dashboardEntries(user)
    val rows = entries.map(entry => {
      val project = entry.project_name[String]
      val phase = entry.phase_name[String]
      val status = entry.display_status[String]
      val tasksOverdueDetail: DynDoc = entry.tasks_overdue[Document]
      val tasksOverdue = tasksOverdueDetail.value[String]
      s"Project: $project, Phase: $phase, Status: $status, Tasks-Overdue: $tasksOverdue"
    })
    rows.mkString("\n")
  }

  private def whoAmI(user: DynDoc): String = {
    s"You are ${PersonApi.fullName(user)}"
  }

  private def slackManage(op: String, names: List[String]): DynDoc => String = {
    if (!op.toLowerCase.matches("invite|status"))
      return _ => s"Unknown operation: $op"
    val persons: Seq[DynDoc] = names match {
      case firstName +: Nil => BWMongoDB3.persons.find(Map("first_name" -> Map($regex -> s"(?i)$firstName")))
      case firstName +: lastName +: Nil => BWMongoDB3.persons.find(Map("first_name" -> Map($regex -> s"(?i)$firstName"),
          "last_name" -> Map($regex -> s"(?i)$lastName")))
      case _ => Nil
    }
    persons.length match {
      case 0 => _ => s"""Unknown user name: ${names.mkString(" ")}"""
      case 1 => op.toLowerCase match {
        case "invite" => SlackApi.invite(persons.head)
        case "status" => SlackApi.status(persons.head)
      }
      case _ => _ => s"""Ambiguous user name: ${names.mkString(" ")}"""
    }
  }

  private object commandLineParser extends RegexParsers {

    // Type for CLI processor parsers ...
    private type CLIP = Parser[DynDoc => String]

    // Help command ...
    private lazy val helpParser: CLIP = "(?i)help" ^^ {_ => help}

    // Slack membership command ...
    private lazy val slackManageParser: CLIP = "slack" ~> ("invite" | "status") ~ rep1("\\S+".r) ^^
      {parseResult => slackManage(parseResult._1, parseResult._2)}

    // List entities command ...
    private lazy val entityNamesParser: Parser[String] =
      "(?i)documents?".r ^^ {_ => "documents"} |
      "(?i)projects?".r ^^ {_ => "projects"} |
      "(?i)tasks?".r ^^ {_ => "tasks"}

    private lazy val listEntitiesParser: CLIP =
        "(?i)list|show|display|query".r ~> entityNamesParser ^^ {
      case "documents" => listDocuments
      case "projects" => listProjects
      case "tasks" => listTasks
    }

    // Dashboard command ...
    private lazy val dashboardParser: CLIP = "(?i)dash(?:board)?".r ^^ {_ => dashboard}

    // Who am I command ...
    private lazy val whoAmIParser: CLIP = "who" ~ "am" ~ "I" ^^ {_ => whoAmI}

    private lazy val none: CLIP = ".*".r ^^ {_ => help}

    private lazy val allCommands: CLIP =
        dashboardParser | helpParser | listEntitiesParser | whoAmIParser | slackManageParser | none

    def cliProcessor(command: String, user: DynDoc): String = {
      parseAll(allCommands, command) match {
        case Success(result, _) => result(user)
        case NoSuccess(result, _) => result
      }
    }

    def testParser(command: String): String = {
      val sanjay: DynDoc = BWMongoDB3.persons.find(Map("last_name" -> "Dasgupta")).head
      parseAll(allCommands, command) match {
        case Success(result, _) => result(sanjay)
        case NoSuccess(result, _) => result
      }
    }

  }

  def main(args: Array[String]): Unit = {
    println(commandLineParser.testParser("list documents"))
    println(commandLineParser.testParser("list projects"))
    println(commandLineParser.testParser("list tasks"))
    println(commandLineParser.testParser("slack status caroline"))
    //println(commandLineParser.testParser("list phases"))
  }

}
