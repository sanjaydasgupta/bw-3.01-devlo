package com.buildwhiz.api

import com.buildwhiz.baf2.PersonApi
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, CryptoUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class Person extends HttpServlet with RestUtils with CryptoUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def updater(d: Document): Document = {
      val d2 = new Document(d)
      d2.put("password", md5(d.getString("first_name")))
      d2.put("roles", List.empty[String].asJava)
      d2.put("project_ids", Seq.empty[ObjectId].asJava)
      d2
    }

    def duplicateChecker(d: Document): Document = {
      val dyn: DynDoc = d
      val workEmail = dyn.emails[Many[Document]].filter(_.`type`[String] == "work").head.email[String]
      val d2 = Map("emails" -> Map("type" -> "work", "email" -> workEmail))
      d2
    }

    if (!userHasRole(request, "BW-Admin"))
      throw new IllegalArgumentException("Not authorized")
    val person: DynDoc = handleRestPost(request, response, "persons", Some(duplicateChecker), Some(updater))
    val personNameAndId = s"'${PersonApi.fullName(person)}' (${person._id[ObjectId]})"
    BWLogger.audit(getClass.getName, "handlePost", s"""Added Person $personNameAndId""", request)
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    if (!userHasRole(request, "BW-Admin"))
      throw new IllegalArgumentException("Not authorized")
    handleRestPut(request, response, "Person", "persons")
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    if (!userHasRole(request, "BW-Admin"))
      throw new IllegalArgumentException("Not authorized")
    handleRestDelete(request, response, "Person", "persons")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def personSorter(a: Document, b: Document): Boolean = {
      def name(d: DynDoc): String = PersonApi.fullName(d)
      name(a) < name(b)
    }

    handleRestGet(request, response, "Person", "persons", Set("password"), Some(personSorter))
  }

}