package com.buildwhiz.infra

import BWMongoDB3._
import scala.collection.JavaConverters._
import scala.collection.mutable
import org.bson.Document

object UsersForAlfresco extends App {

  val fields = Seq("User Name", "First Name", "Last Name", "E-mail Address", "", "Password", "Company", "Job Title",
    "Location", "Telephone", "Mobile", "Skype", "IM", "Google User Name", "Address", "Address Line 2", "Address Line 3",
    "Post Code", "Telephone", "Fax", "Email")

  println(fields.mkString(", "))
  val persons: Seq[DynDoc] = BWMongoDB3.persons.find().asScala.toSeq
  for (person <- persons) {
    val buffer = mutable.Buffer.empty[String]
    val emails: Seq[DynDoc] = person.emails[DocumentList]
    buffer += emails.filter(_.`type`[String] == "work").head.email[String]
    buffer += person.first_name[String]
    buffer += person.last_name[String]
    buffer += emails.filter(_.`type`[String] == "work").head.email[String]
    buffer += ""
    buffer += person.first_name[String] // will become account's password
    buffer += person.company[String]
    buffer += person.title[String]
    buffer += "" // location
    val phones: Seq[DynDoc] = person.phones[DocumentList]
    buffer += (phones.find(_.`type`[String] == "work") match {
      case Some(p) => p.phone[String]
      case None => ""
    })
    buffer += (phones.find(_.`type`[String] == "mobile") match {
      case Some(p) => p.phone[String]
      case None => ""
    })
    buffer += "" // skype
    buffer += "" // IM
    buffer += "" // Google user-name
    val address: DynDoc = person.address[Document]
    buffer += address.asDoc.get("formatted").asInstanceOf[String]
    buffer += "" // address line 2
    buffer += "" // address line 3
    buffer += address.zip[String]
    buffer += "" // telephone
    buffer += (phones.find(_.`type`[String] == "fax") match {
      case Some(p) => p.phone[String]
      case None => ""
    })
    buffer += (emails.find(_.`type`[String] == "other") match {
      case Some(e) => e.email[String]
      case None => ""
    })
    val buffer2 = buffer.map(e => if (e == null) "" else e)
    println(buffer2.map(f => if (f.contains(",")) s""""$f"""" else f).mkString(","))
  }

}
