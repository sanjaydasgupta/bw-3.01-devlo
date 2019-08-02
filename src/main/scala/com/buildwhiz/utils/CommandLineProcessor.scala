package com.buildwhiz.utils

import scala.util.parsing.combinator.RegexParsers

object CommandLineProcessor {

  def process(command: String): String = {
    commandLineParser.cliProcessor(command)
  }

  private def help(): String = {
    "Commands: list projects, list tasks, list documents"
  }

  private def listDocuments(): String = {
    "You have no documents now"
  }

  private def listProjects(): String = {
    "You have no projects now"
  }

  private def listTasks(): String = {
    "You have no tasks now"
  }

  private object commandLineParser extends RegexParsers {

    // Type for CLI processor parsers ...
    private type CLIP = Parser[String]

    // Help command ...
    private lazy val helpCommand: CLIP = "(?i)help" ^^ {_ => help()}

    // List projects command ...
    private lazy val listDocumentsCommand: CLIP = "(?i)list".r ~ "(?i)documents".r ^^ {_ => listDocuments()}

    // List projects command ...
    private lazy val listProjectsCommand: CLIP = "(?i)list".r ~ "(?i)projects".r ^^ {_ => listProjects()}

    // List tasks command ...
    private lazy val listTasksCommand: CLIP = "(?i)list".r ~ "(?i)tasks".r ^^ {_ => listTasks()}

    private lazy val none: CLIP = ".*".r ^^ {_ => help()}

    private lazy val allCommands: CLIP = helpCommand | listDocumentsCommand | listProjectsCommand | listTasksCommand | none

    def cliProcessor(command: String): String = {
      parseAll(allCommands, command) match {
        case Success(result, _) => result
        case NoSuccess(result, _) => result
      }
    }

  }

  def main(args: Array[String]): Unit = {
    println(process("list documents"))
    println(process("list projects"))
    println(process("list tasks"))
    println(process("list tags"))
    println(process("foo bar"))
  }
}
