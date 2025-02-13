package com.buildwhiz.slack

import java.io.ByteArrayOutputStream
import com.buildwhiz.baf2.{ActivityApi, DashboardEntries, PersonApi, TaskList}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils}

import javax.servlet.http.HttpServletRequest
import org.apache.http.Consts
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClients
import org.bson.Document
import org.bson.types.ObjectId

import java.net.URLEncoder
import scala.io.Source
import scala.jdk.CollectionConverters._

object SlackApi extends DateTimeUtils {

  //xoxp-644537296277-644881565541-1120001615844-38c9f9525da75f895634393576d2f75c
  //xoxb-644537296277-708634256516-fXAKmdo1h467oFMx8ZoH8vg2

  def userBySlackId(slackUserId: String): Option[DynDoc] = {
    BWMongoDB3.persons.find(Map("slack_id" -> slackUserId)).headOption
  }

  def invite(who: DynDoc): DynDoc => String = {
    user => {
      s"${PersonApi.fullName(user)} wishes to invite ${PersonApi.fullName(who)}"
    }
  }

  def status(who: DynDoc): DynDoc => String = {
    _ => {
      val slackStatus = if (who.has("slack_id"))
        "Connected"
      else
        "Not connected"
      s"${PersonApi.fullName(who)}'s Slack status is '$slackStatus'"
    }
  }

//  def pushHomePages(): Unit = {
//    BWLogger.log(getClass.getName, "LOCAL", "pushHomePages: ENTRY")
//    try {
//      val slackUsers: Seq[DynDoc] = BWMongoDB3.persons.find(Map("slack_id" -> Map($exists -> true)))
//      for (slackUser <- slackUsers) {
//        val slackId = slackUser.slack_id[String]
//        SlackApi.viewPublish(None, slackId, None)
//      }
//    } catch {
//      case t: Throwable =>
//        BWLogger.log(getClass.getName, "LOCAL",
//            s"pushHomePages: ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
//    }
//    BWLogger.log(getClass.getName, "LOCAL", "pushHomePages: EXIT-OK")
//  }
//
  def sendToUser(stringOrBlocks: Either[String, Seq[DynDoc]], user: Either[DynDoc, ObjectId],
      optProjectOid: Option[ObjectId] = None, optRequest: Option[HttpServletRequest] = None): Unit = {
    BWLogger.log(getClass.getName, "LOCAL", "sendToUser: ENTRY")
    val personRecord: DynDoc = user match {
      case Left(dd) => dd
      case Right(oid) => PersonApi.personById(oid)
    }
    val slackIdValues: Seq[String] = personRecord.get[AnyRef]("slack_id") match {
      case Some(id: String) => Seq(id)
      case Some(dict: Document) => optProjectOid match {
        case None => dict.values.asScala.toSeq.map(_.asInstanceOf[String])
        case Some(prOid: ObjectId) => if (dict.containsKey(prOid)) Seq(dict.getString(prOid)) else Nil
      }
      case _ => Nil
    }
    if (slackIdValues.nonEmpty) {
      for (slackChannel <- slackIdValues) {
        sendToChannel(stringOrBlocks, slackChannel, request=optRequest)
      }
      BWLogger.log(getClass.getName, "LOCAL", "sendToUser: EXIT-OK", optRequest)
    } else {
      val message = optProjectOid match {
        case None => s"EXIT-ERROR: User ${PersonApi.fullName(personRecord)} not on Slack. Message dropped: '$stringOrBlocks'"
        case Some(prOid) => s"EXIT-ERROR: User ${PersonApi.fullName(personRecord)} not on Slack for project $prOid." +
          s"Message dropped: '$stringOrBlocks'"
      }
      BWLogger.log(getClass.getName, "LOCAL", s"sendToUser: $message")
    }
  }

  def sendNotification(message: String, user: Either[DynDoc, ObjectId],
      optProjectOid: Option[ObjectId] = None, optRequest: Option[HttpServletRequest] = None): Unit = {
    val info: DynDoc = BWMongoDB3.instance_info.find().head
    val instanceName = info.instance[String]
    sendToUser(Left(s"$message on '$instanceName'"), user, optProjectOid, optRequest)
  }

  def sendToChannel(textOrBlocks: Either[String, Seq[DynDoc]], channel: String, optThreadTs: Option[String] = None,
      request: Option[HttpServletRequest] = None): Unit = {
    // https://api.slack.com/messaging/sending
    // https://api.slack.com/messaging/retrieving#finding_threads
    // https://api.slack.com/messaging/managing#threading
    BWLogger.log(getClass.getName, "LOCAL", "sendToChannel: ENTRY")
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/chat.postMessage")
    post.setHeader("Authorization",
      "Bearer xoxb-644537296277-708634256516-fXAKmdo1h467oFMx8ZoH8vg2")
    post.setHeader("Content-Type", "application/json; charset=utf-8")
    val bodyText = (textOrBlocks, optThreadTs) match {
      case (Left(messageText), None) => s"""{"text": "$messageText", "channel": "$channel"}"""
      case (Left(messageText), Some(threadTs)) => s"""{"text": "$messageText", "channel": "$channel", "thread_ts": "$threadTs"}"""
      case (Right(blocks), None) =>
        val blocksText = blocks.map(_.asDoc.toJson).mkString(",")
        s"""{"blocks": [$blocksText], "channel": "$channel"}"""
      case (Right(blocks), Some(threadTs)) =>
        val blocksText = blocks.map(_.asDoc.toJson).mkString(",")
        s"""{"blocks": [$blocksText], "channel": "$channel", "thread_ts": "$threadTs"}"""
    }
    BWLogger.log(getClass.getName, "LOCAL", s"sendToChannel: $bodyText")
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad chat.postMessage status: $contentString")
    BWLogger.log(getClass.getName, "LOCAL", s"sendToChannel: EXIT-OK ($contentString)")
  }

  // https://api.slack.com/messaging/interactivity

  def createSectionWithAccessory(descriptionText: String, accessory: DynDoc, extraFields: Seq[(String, Any)] = Nil):
      DynDoc = createSection(descriptionText, ("accessory" -> accessory) +: extraFields)

  def createSection(descriptionText: String, extraFields: Seq[(String, Any)] = Nil): DynDoc = Map(
    "type" -> "section",
    "text" -> Map("type" -> "mrkdwn", "text" -> descriptionText),
  ) ++ extraFields

  def createButton(buttonText: String, buttonValue: String, actionId: String): DynDoc = Map(
    "type" -> "button", "value" -> buttonValue, "action_id" -> actionId,
    "text" -> Map("type" -> "plain_text", "text" -> buttonText, "emoji" -> true)
  )

  def createInputBlock(label: String, id: String, element: DynDoc): DynDoc = Map(
    "type" -> "input",
    "block_id" -> id,
    "label"-> Map("type" -> "plain_text", "text" -> label, "emoji" -> true),
    "element"-> element
  )

  def createPlainTextInput(actionId: String, multiline: Boolean = false): DynDoc = Map(
    "type" -> "plain_text_input",
    "action_id" -> actionId,
    "multiline" -> multiline
  )

  def createDivider(): DynDoc = Map("type" -> "divider")

  def createDatePicker(placeholderText: String, actionId: String, initialDate: String): DynDoc = Map(
    "type" -> "datepicker", "action_id" -> actionId,
    if (initialDate.trim.isEmpty)
      "placeholder" -> Map("type" -> "plain_text", "text" -> placeholderText, "emoji" -> true)
    else
      "initial_date" -> initialDate
  )

  def createMultipleChoiceMessage(optionDescriptionsAndTexts: Seq[(String, String)], messageId: String):
      Seq[DynDoc] = {
    optionDescriptionsAndTexts.map(dt => createSectionWithAccessory(dt._1,
        createButton("Select", s"$messageId-${dt._2}", s"action-id-$messageId-${dt._2}")))
  }

  def createModalView(title: String, id: String, blocks: Seq[DynDoc], withSubmitButton: Boolean = false): Document = {
    val basicFields = Seq(
      "type" -> "modal",
      //"callback_id" -> s"$id-modal",
      "title" -> Map("type" -> "plain_text", "text" -> title),
      "close" -> Map("type" -> "plain_text", "text" -> "Cancel", "emoji" -> true),
      "blocks" -> blocks
    )

    val allFields = if (withSubmitButton)
      ("submit" -> Map("type" -> "plain_text", "text" -> "Submit", "emoji" -> true)) +: basicFields
    else
      basicFields

    allFields.toMap
  }

  def createSelectInputBlock(label: String, placeHolderText: String, id: String, options: Seq[(String, String)]):
      Map[String, _] = {
    Map(
      "type" -> "input",
      "block_id" -> s"$id-block",
      "label"-> Map("type" -> "plain_text", "text" -> label),
      "element"-> Map(
        "type" -> "static_select",
        "placeholder" -> Map("type" -> "plain_text", "text" -> placeHolderText),
        "action_id" -> s"$id-options",
        "options" -> options.map(option =>
          Map("text" -> Map("type" -> "plain_text", "text" -> option._1), "value" -> s"$id-${option._2}"))
      )
    )
  }

  def createCheckboxInputBlock(label: String, id: String, options: Seq[(String, String)]):
      Map[String, _] = {
    Map(
      "type" -> "input",
      "block_id" -> s"$id-block",
      "label"-> Map("type" -> "plain_text", "text" -> label, "emoji" -> true),
      "element"-> Map(
        "type" -> "checkboxes",
        "options" -> options.map(option =>
          Map("text" -> Map("type" -> "plain_text", "text" -> option._1), "value" -> s"$id-${option._2}"))
      )
    )
  }

  def createTaskStatusUpdateView(bwUser: DynDoc, theActivity: DynDoc): Document = {
    val (name, status) = (theActivity.name[String], theActivity.status[String])
    val endDates = Seq("end_date_pessimistic", "end_date_likely", "end_date_optimistic").map(dateType => {
      if (theActivity.has(dateType))
        dateTimeString(theActivity.asDoc.getLong(dateType), Some(bwUser.tz[String])).split(" ").head
      else
        "1970-01-01"
    })
    val dateBlocks = Seq(("optimistic", endDates(2)), ("likely", endDates(1)), ("pessimistic", endDates.head)).
      map(dt => createInputBlock(s"Select ${dt._1} completion date", s"BW-tasks-update-completion-date-${dt._1}",
        SlackApi.createDatePicker("Select date", s"BW-tasks-update-completion-date-${dt._1}", dt._2)))
    val viewBlocks: Seq[DynDoc] = if (status == "running") {
      val percentComplete = ActivityApi.percentComplete(theActivity)
      val percentCompleteBlock = createInputBlock("Enter % complete (100% means complete)",
          "BW-tasks-update-percent-complete", Map("type" -> "plain_text_input",
          "action_id" -> s"BW-tasks-update-percent-complete",
          "placeholder" -> Map("type" -> "plain_text", "text" -> percentComplete)))
      val commentBlock = createInputBlock("Comments for this update", "BW-tasks-update-comments",
          Map("type" -> "plain_text_input", "multiline" -> true, "action_id" -> s"BW-tasks-update-comments"))
      val topBlock: DynDoc = Map("type" -> "section", "text" -> Map("type" -> "mrkdwn", "text" ->
          s"Update status of active task *'$name'* by modifying fields below. Then click *Submit* button"))
      topBlock +: createDivider() +:
        (dateBlocks ++ Seq(createDivider(), percentCompleteBlock, commentBlock))
    } else if (status == "ended") {
      val topBlock: DynDoc = Map("type" -> "section", "text" -> Map("type" -> "mrkdwn", "text" ->
          s"This task is complete. There is nothing to update."))
      Seq(topBlock)
    } else {
      val topBlock: DynDoc = Map("type" -> "section", "text" -> Map("type" -> "mrkdwn", "text" ->
         s"Update status of scheduled task *'$name'* by modifying fields below. Then click *Submit* button"))
      topBlock +: dateBlocks
    }
    val tasksModalView = createModalView(s"Task '$name'", "BW-tasks-update", viewBlocks, status != "ended")
    Map("view" -> tasksModalView, "response_action" -> "push")
  }

  def createTaskSelectionView(bwUser: DynDoc): Document = {
    val assignments = TaskList.uniqueAssignments(bwUser)
    val tasks = assignments.map(assignment => {
      val id = assignment.activity_id[ObjectId]
      val project = assignment.project_name[String]
      val phase = assignment.phase_name[String]
      val process = assignment.process_name[String]
      val name = assignment.activity_name[String]
      val status = assignment.status[String]
      val end = assignment.end_datetime[String].split(" ").head
      val start = assignment.start_datetime[String].split(" ").head
      (s"$name/$phase/$project ($status)", id.toString)
    })
    val taskOptions = createSelectInputBlock("Select a task and click 'Submit' for details", "Select a task",
        "BW-tasks-update-display-task-list", tasks)
    val taskCheckboxes = createCheckboxInputBlock("Choose 'Status' or 'Documents'",
        "BW-tasks-update-display-checkboxes", Seq(("Status", "status"), ("Documents", "documents")))
    val tasksModalView = createModalView("Current Tasks", "BW-tasks", Seq(taskOptions, createDivider(), taskCheckboxes),
        withSubmitButton = true)
    Map("view" -> tasksModalView, "response_action" -> "push")
  }

  def createDashboardView(user: DynDoc): Document = {
    val dashboardEntries: Seq[DynDoc] = DashboardEntries.dashboardEntries(user)
    val dashboardInfoArray = dashboardEntries.map(entry => {
      val project = entry.project_name[String]
      val phase = entry.phase_name[String]
      val phaseId = entry.phase_id[String]
      val status = entry.display_status[String]
      val tasksOverdueDetail: DynDoc = entry.tasks_overdue[Document]
      val tasksOverdue = tasksOverdueDetail.value[String]
      (s"$project/$phase ($status) Overdue: $tasksOverdue", phaseId)
    })
    val dashboardOptions = createSelectInputBlock("Select item and click 'Submit' for details",
        "Select dashboard entry", "BW-dashboard-detail-phase", dashboardInfoArray)
    val dashboardModalView = createModalView("Dashboard Display", "BW-dashboard", Seq(dashboardOptions),
        withSubmitButton = true)
    Map("view" -> dashboardModalView, "response_action" -> "push")
  }

  def viewOpen(viewText: String, triggerId: String): Unit = {
    BWLogger.log(getClass.getName, "LOCAL", "openView: ENTRY")
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/views.open")
    post.setHeader("Authorization",
      "Bearer xoxb-644537296277-708634256516-fXAKmdo1h467oFMx8ZoH8vg2")
    post.setHeader("Content-Type", "application/json; charset=utf-8")
    val bodyText = s"""{"view": $viewText, "trigger_id": "$triggerId"}"""
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad views.open status: $contentString")
    BWLogger.log(getClass.getName, "LOCAL", s"openView: EXIT-OK ($contentString)")
  }

  def viewPush(viewText: String, triggerId: String): Unit = {
    BWLogger.log(getClass.getName, "LOCAL", "pushView: ENTRY")
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/views.push")
    post.setHeader("Authorization",
      "Bearer xoxb-644537296277-708634256516-fXAKmdo1h467oFMx8ZoH8vg2")
    post.setHeader("Content-Type", "application/json; charset=utf-8")
    val bodyText = s"""{"view": $viewText, "trigger_id": "$triggerId"}"""
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad views.open status: $contentString")
    BWLogger.log(getClass.getName, "LOCAL", s"pushView: EXIT-OK ($contentString)")
  }

  def viewPublish(viewText: String, slackUserId: String, optHash: Option[String] = None): Unit = {
    BWLogger.log(getClass.getName, "LOCAL", "viewPublish: ENTRY")
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/views.publish")
    post.setHeader("Authorization",
      "Bearer xoxb-644537296277-708634256516-fXAKmdo1h467oFMx8ZoH8vg2")
    post.setHeader("Content-Type", "application/json; charset=utf-8")
    val hash = optHash match {
      case Some(h) => h
      case None => System.nanoTime().toString
    }
    //val bodyText = s"""{"view": $viewText, "user_id": "$userId", "hash": "$hash"}"""
    val bodyText = s"""{"view": $viewText, "user_id": "$slackUserId"}"""
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad views.open status: $contentString")
    BWLogger.log(getClass.getName, "LOCAL", s"viewPublish: EXIT-OK ($contentString)")
  }

  def main(args: Array[String]): Unit = {
//    val specs = Seq(("One description", "one"), ("Two description", "two"), ("Three description", "three"))
//    val mcm = createMultipleChoiceMessage(specs, "message_id")
//    println(mcm.map(_.asDoc.toJson).mkString(",\n"))
    print(homePage())
  }

  private def homePage(optUser: Option[DynDoc] = None): String = {
    val itemsBasic: Seq[(String, String)] = Seq(
      ("Work Context", "(Project: *not selected*, Phase: *not selected*"),
      ("Deliverables", "(click button to see list of deliverables)"),
      ("Issues", "(click button to see list of issues)"),
      ("Projects", "(click button to see list of projects)"),
      ("Organizations", "(click button to see list of organizations)"),
      ("Profile", "(contact info, skills, password, etc)")
    )
    val items: Seq[(String, String)] = optUser match {
      case Some(userRecord) if PersonApi.isBuildWhizAdmin(Right(userRecord)) =>
        itemsBasic ++ Seq(("Active Users", "(click button to see list of tasks)"))
      case _ => itemsBasic
    }
    val blocks = items.map(item => {
      val button = createButton(item._1, s"go-${item._1}", s"action-id-${item._1}")
      createSectionWithAccessory(s"*${item._1}* ${item._2}", button, Seq(("block_id", s"block-id-${item._1}")))
    })
    val page: DynDoc = Map("type" -> "home", "blocks" -> blocks)
    page.asDoc.toJson
  }

  def invokeSlackHandler(uid: String, urlParams: Map[String, String], bodyJson: String = "",
      request: HttpServletRequest): DynDoc = {
    //BWLogger.log(getClass.getName, "LOCAL", s"ENTRY-invokeSlackHandler")
    val enc = (p: String) => URLEncoder.encode(p, "utf8")
    val urlParamString = urlParams.map(p => "%s=%s".format(enc(p._1), enc(p._2))).mkString("&")
    val httpPost = new HttpPost(s"http://localhost:3000/SlackHandler?uid=$uid&$urlParamString")
    if (bodyJson.nonEmpty) {
      httpPost.setHeader("Content-Type", "application/json; charset=utf-8")
      httpPost.setEntity(new StringEntity(bodyJson))
    }
//    request.getHeaderNames.asScala.foreach(hdrName => httpPost.setHeader(hdrName, request.getHeader(hdrName)))
    val clientIp = request.getHeader("X-FORWARDED-FOR") match {
      case null => request.getRemoteAddr
      case ip => ip
    }
    httpPost.setHeader("X-FORWARDED-FOR", clientIp)
    //val t0 = System.currentTimeMillis()
    val nodeResponse = HttpClients.createDefault().execute(httpPost)
    val nodeEntityString = Source.fromInputStream(nodeResponse.getEntity.getContent).getLines().mkString("\n")
    val nodeEntityDoc: DynDoc = Document.parse(nodeEntityString)
    //if (nodeEntityDoc.ok[Int] == 1) {
    //  val delay = System.currentTimeMillis() - t0
    //  BWLogger.log(getClass.getName, "LOCAL", s"AUDIT-invokeSlackHandler(time: $delay)")
    //} else {
    //  BWLogger.log(getClass.getName, "LOCAL", s"ERROR-invokeSlackHandler: ${nodeEntityString}")
    //}
    nodeEntityDoc
  }

  // https://api.slack.com/tutorials/design-expense-block-kit

}
