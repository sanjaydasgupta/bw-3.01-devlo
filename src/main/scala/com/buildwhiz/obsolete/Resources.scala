package com.buildwhiz.obsolete

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB2
import com.buildwhiz.infra.BWMongoDB2._
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

class Resources extends HttpServlet {
  
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val uriParts = request.getRequestURI.split("/").toList
    val writer = response.getWriter
    val html: Try[String] = uriParts.reverse match {
      case "Resources" :: _ =>
        basePage(request, response)
      case personId :: "Resources" :: _ =>
        personPage(request, response, personId)
      case _ => throw new Exception("No such page!")
    }
    html match {
      case Success(s) => writer.println(s)
      case Failure(t) => t.printStackTrace(writer)
        writer.println(s"""<div style="text-align: center; background-color: red;">Internal ERROR: ${t.getClass.getSimpleName}(${t.getMessage})</div>""")
    }
    writer.println("</body></html>")
  }

  private def basePage(request: HttpServletRequest, response: HttpServletResponse): Try[String] = {
    val writer = response.getWriter
    writer.println(s"<!DOCTYPE html>")
    writer.println(s"<html><head><title>BuildWhiz Persons</title></head>")
    writer.println( s"""<body><h2 align="center">BuildWhiz Persons (<a href="index.html">home</a>)</h2>""")
    Try {
      val sb = new StringBuilder()
      sb.append( """<table align="center">""")
      val persons: Seq[BWAccessor] = BWMongoDB2.persons.*
      for (person <- persons) {
        val id: ObjectId = person._id.?[ObjectId]
        val firstName = person.first_name.?[String]
        val lastName = person.last_name.?[String]
        val emails: Seq[BWAccessor] = person.emails.*
        val workEmail = emails.find(_.`type`.?[String] == "work").
          map(_.email.?[String]).getOrElse("unknown")
        sb.append( s"""<tr><td align="center"><a href="${request.getRequestURI}/$id">$firstName $lastName ($workEmail)</a></td></tr>""")
      }
      sb.append("</table>")
      sb.toString()
    }
  }

  private def personPage(request: HttpServletRequest, response: HttpServletResponse, personId: String): Try[String] = {

    def inboxHtml(projectId: ObjectId, phaseName: String, inbox: Seq[BWAccessor]): String = {
      val docIds: Seq[ObjectId] = inbox.map(_.document_master_id.?[ObjectId])
      val documents: Seq[BWAccessor] = docIds.map(id => BWMongoDB2.document_master(id).*.head)
      val namesAndIds: Seq[(String, String)] = documents.map(d => (d.name.?[String], d._id.?[ObjectId].toString))
      val linkedNames: Iterable[String] = namesAndIds.map(nid =>
        s"""<a href="../file_download/${nid._1}?project_id=$projectId&phase_name=$phaseName&document_id=${nid._2}">${nid._1}</a>""")
      linkedNames.mkString(", ")
    }

    def outboxHtml(projectId: ObjectId, phaseName: String, roleName: String, outbox: Seq[BWAccessor]): String = {
      //response.setContentType("text/html;charset=UTF-8");
      val sb = new StringBuilder()
      val returnUri = request.getRequestURI
      for (i <- 0 until outbox.size) {
        val entry = outbox(i)
        val documentId = entry.document_master_id.?[ObjectId]
        val forReview = entry.for_review.?[Boolean]
        val messageName = entry.message_name.?[String]
        val document: BWAccessor = BWMongoDB2.document_master(documentId).*.head
        val documentName = document.name.?[String]
        val inputFileHtml = """<input type="file" name="file" id="file" />"""
        val submitHtml = """<input type="submit" value="Upload" name="upload" id="upload" />"""
        if (forReview) {
          val authorRoleName = entry.author_role_name.?[String]
          val action = s"action=../file_upload?project_id=$projectId&phase_name=$phaseName&role_name=$roleName" +
            s"&document_id=$documentId&for_review=$forReview&return_uri=$returnUri&author_role_name=$authorRoleName" +
            s"&message_name=$messageName"
          val checkBox = """<br/><input type="checkbox" name="manual_review_result" value="manual_review_result" checked>Accepted"""
          val formStart = s"""<form method="POST" $action enctype="multipart/form-data" style="margin: 0;">"""
          sb.append(s"""$formStart $documentName-review: $inputFileHtml $checkBox $submitHtml</form>""")
        } else {
          val action = s"action=../file_upload?project_id=$projectId&phase_name=$phaseName&role_name=$roleName" +
            s"&document_id=$documentId&for_review=$forReview&return_uri=$returnUri" +
            s"&message_name=$messageName"
          val formStart = s"""<form method="POST" $action enctype="multipart/form-data" style="margin: 0;">"""
          sb.append(s"""$formStart $documentName: $inputFileHtml $submitHtml</form>""")
        }
        if (i < outbox.size - 1)
          sb.append("<hr/>")
      }
      sb.toString()
    }

    val writer = response.getWriter
    writer.println(s"<!DOCTYPE html>")
    writer.println(s"<html><head><title>BuildWhiz Resource '$personId'</title></head>")
    Try {
      val person: BWAccessor = BWMongoDB2.persons(personId).*.head
      val (firstName, lastName, projectIds) = (person.first_name.?[String], person.last_name.?[String],
        person.project_ids.?[Many[ObjectId]])
      val sb = new StringBuilder()
      sb.append( s"""<body><h2 align="center">$firstName $lastName's BuildWhiz Page (<a href="../index.html" style="font-weight: normal">home</a>)</h2>""")
      sb.append( s"""<body><h3 align="center"><a href="../Resources">Back to Resources Page</a></h3>""")
      sb.append( """<div style="height: 30%; background-color: gray;">""")
      sb.append( """<div style="text-align: center; font-weight: bold; background-color: blue; color: white;">Project Info Table</div>""")
      val header = List(("Project", 25), ("Phase", 12), ("Role", 13), ("Inbox", 25), ("Outbox", 25)).
        map(s => s"""<td width="${s._2}%" align="center">${s._1}</td>""").mkString
      sb.append(s"""<table border="1" style="width: 100%; background-color: white;"><tr style="font-weight: bold;">$header</tr>""")
      for (projectId <- projectIds) {
        val project: BWAccessor = BWMongoDB2.projects(projectId).*.head
        val projectName = project.name.?[String]
        val phases: Seq[BWAccessor] = project.phases.*
        for (phase <- phases) {
          val phaseName = phase.name.?[String]
          val roleAssignments: Seq[BWAccessor] = phase.role_assignments.*
          for (roleAssignment <- roleAssignments) {
            if (roleAssignment.person_id.?[ObjectId].toString == personId) {
              val roleName = roleAssignment.name.?[String]
              val inbox: Seq[BWAccessor] = roleAssignment.inbox.*
              val outbox: Seq[BWAccessor] = roleAssignment.outbox.*
              val row = List(projectName, phaseName, roleName, inboxHtml(projectId, phaseName, inbox),
                outboxHtml(projectId, phaseName, roleName, outbox)).
                map(d => s"""<td align="center">$d</td>""").mkString("<tr>", "", "</tr>")
              sb.append(row)
            }
          }
        }
      }
      sb.append("</table></div>")
      sb.toString()
    }
  }

}
