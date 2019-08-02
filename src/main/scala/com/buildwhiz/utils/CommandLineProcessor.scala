package com.buildwhiz.utils

import com.buildwhiz.infra.DynDoc

import scala.util.parsing.combinator.RegexParsers

object CommandLineProcessor {

  def process(command: String, user: DynDoc): String = {
    commandLineParser.cliProcessor(command, user)
  }

  private def help(user: DynDoc): String = {
    """Commands:
      |  list projects,
      |  list tasks,
      |  list documents
      |  who am I""".stripMargin
  }

  private def listDocuments(user: DynDoc): String = {
    "You have no documents now"
  }

  private def listProjects(user: DynDoc): String = {
    "You have no projects now"
  }

  private def listTasks(user: DynDoc): String = {
    "You have no tasks now"
  }

  private def whoAmI(user: DynDoc): String = {
    s"You are ${user.first_name[String]} ${user.last_name[String]}"
  }

  private object commandLineParser extends RegexParsers {

    // Type for CLI processor parsers ...
    private type CLIP = Parser[DynDoc => String]

    // Help command ...
    private lazy val helpParser: CLIP = "(?i)help" ^^ {_ => help}

    // List projects command ...
    private lazy val listDocumentsParser: CLIP = "(?i)list".r ~ "(?i)documents".r ^^ {_ => listDocuments}

    // List projects command ...
    private lazy val listProjectsParser: CLIP = "(?i)list".r ~ "(?i)projects".r ^^ {_ => listProjects}

    // List tasks command ...
    private lazy val listTasksParser: CLIP = "(?i)list".r ~ "(?i)tasks".r ^^ {_ => listTasks}

    // Who am I command ...
    private lazy val whoAmIParser: CLIP = "(?i)who".r ~ "(?i)am".r ~ "(?i)I".r ^^ {_ => whoAmI}

    private lazy val none: CLIP = ".*".r ^^ {_ => help}

    private lazy val allCommands: CLIP = helpParser | listDocumentsParser | listProjectsParser | listTasksParser |
        whoAmIParser | none

    def cliProcessor(command: String, user: DynDoc): String = {
      parseAll(allCommands, command) match {
        case Success(result, _) => result(user)
        case NoSuccess(result, _) => result
      }
    }

  }

}
