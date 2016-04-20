package com.buildwhiz.obsolete

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB2
import com.buildwhiz.infra.BWMongoDB2._
import org.bson.types.ObjectId

import scala.util.{Failure, Success, Try}

class Projects extends HttpServlet {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val uriParts = request.getRequestURI.split("/").toList
    val writer = response.getWriter
    val html: Try[String] = uriParts.reverse match {
      case "Projects" :: _ =>
        basePage(request, response)
      case projectId :: "Projects" :: _ =>
        projectPage(request, response, projectId)
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
    writer.println(s"<html><head><title>BuildWhiz Projects</title></head>")
    writer.println( s"""<body><h2 align="center">BuildWhiz Projects (<a href="index.html">home</a>)</h2>""")
    Try {
      val sb = new StringBuilder()
      sb.append( """<table align="center">""")
      val projects: Seq[BWAccessor] = BWMongoDB2.projects.*
      for (project <- projects) {
        val id: ObjectId = project._id.?[ObjectId]
        val name = project.name.?[String]
        sb.append( s"""<tr><td align="center"><a href="${request.getRequestURI}/$id">$name</a></td></tr>""")
      }
      sb.append("</table>")
      sb.toString()
    }
  }

  private def projectPage(request: HttpServletRequest, response: HttpServletResponse, projectId: String): Try[String] = {

    val writer = response.getWriter
    writer.println(s"<!DOCTYPE html>")
    writer.println(s"<html><head><title>BuildWhiz Project '$projectId'</title></head>")
    Try {
      val project: BWAccessor = BWMongoDB2.projects(projectId).*.head
      val projectName = project.name.?[String]
      val projectStatus = project.status.?[String]
      val customerId = project.customer_id.?[ObjectId]
      val customerName = BWMongoDB2.customers(customerId).*.head.name.?[String]
      val controllerId = project.controller_person_id.?[ObjectId]
      val controller: BWAccessor = BWMongoDB2.persons(controllerId).*.head
      val ctrlrFirstName = controller.first_name.?[String]
      val ctrlrLastName = controller.last_name.?[String]
      val sb = new StringBuilder()
      sb.append( s"""<body><h2 align="center">BuildWhiz Project '$projectName' (<a href="../index.html" style="font-weight: normal">home</a>)</h2>""")
      sb.append( s"""<body><h3 align="center"><a href="../Projects">Back to Projects Page</a></h3>""")
      val header = List("Id", "Name", "Customer", "Controller", "Status").map(s => s"""<td align="center">$s</td>""").mkString
      val values = List(projectId, projectName, customerName, s"$ctrlrFirstName $ctrlrLastName", projectStatus).map(s => s"""<td align="center">$s</td>""").mkString
      sb.append(s"""<table border="1" style="align: center;">""")
      sb.append(s"""<tr style="font-weight: bold;">$header</tr><tr>$values</tr>""")
      sb.append("</table>")
      val phases: Seq[BWAccessor] = project.phases.*
      sb.append(s"""<div style="text-align: center; width: 100%">Project Details</div>""")
      sb.append(s"""<table border="1" style="align: center;">""")
      val header2 = List("Phase", "Status", "BPMN", "Role", "Person", "Inbox", "Outbox").
        map(s => s"""<td align="center">$s</td>""").mkString
      sb.append(s"""<tr style="font-weight: bold;">$header2</tr>""")
      for (phase <- phases) {
        val phaseName = phase.name.?[String]
        val phaseStatus = phase.status.?[String]
        val bpmnName = phase.bpmn_name.?[String]
        val roles: Seq[BWAccessor] = phase.role_assignments.*
        for (role <- roles) {
          val roleName = role.name.?[String]
          val personId = role.person_id.?[ObjectId]
          val person: BWAccessor = BWMongoDB2.persons(personId).*.head
          val personName = s"${person.first_name.?[String]} ${person.last_name.?[String]}"
          val inbox: Seq[BWAccessor] = role.inbox.*
          val inboxDocIds = inbox.map(_.document_master_id.?[ObjectId])
          val inboxDocNames = inboxDocIds.map(did => BWMongoDB2.document_master(did).*.head.name.?[String])
          val outbox: Seq[BWAccessor] = role.outbox.*
          val outboxDocIds = outbox.map(_.document_master_id.?[ObjectId])
          val outboxDocNames = outboxDocIds.map(did => BWMongoDB2.document_master(did).*.head.name.?[String])
          val row = Seq(phaseName, phaseStatus, bpmnName, roleName, personName,
            inboxDocNames.mkString(", "), outboxDocNames.mkString(", ")).
            map(s => s"""<td align="center">$s</td>""").mkString
          sb.append(s"<tr>$row</tr>")
        }
      }
      sb.append("</table>")
      sb.toString()
    }
  }

}
