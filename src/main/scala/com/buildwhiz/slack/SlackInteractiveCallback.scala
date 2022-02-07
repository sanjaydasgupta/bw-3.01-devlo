package com.buildwhiz.slack

import com.buildwhiz.baf2.{ProjectApi, ProjectInfo, ProjectInfoSet, ProjectList, RfiDetails, RfiList}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._

class SlackInteractiveCallback extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val payload: DynDoc = Document.parse(parameters("payload"))
      val slackUserInfo: DynDoc = payload.user[Document]
      val slackUserId = slackUserInfo.id[String]
      val bwUserRecord: DynDoc = SlackApi.userBySlackId(slackUserId) match {
        case Some(userRecord) =>
          request.getSession.setAttribute("bw-user", userRecord.asDoc)
          userRecord
        case None =>
          val slackUserName = slackUserInfo.name[String]
          throw new IllegalArgumentException(s"Slack user '$slackUserName' ($slackUserId) unknown to BuildWhiz")
      }
      (payload.get[String]("trigger_id"), payload.get[String]("type")) match {
        case (Some(triggerId), Some("message_action")) =>
          val rootOptions = SlackApi.createSelectInputBlock("Select operation area", "Select option", "BW-root",
            Seq(("Dashboard", "dashboard"), ("Tasks", "tasks")))
          val rootModalView = SlackApi.createModalView("BuildWhiz User Interface", "BW-root", Seq(rootOptions),
            withSubmitButton = true)
          SlackApi.viewOpen(rootModalView.toJson, triggerId)
        case (Some(triggerId), Some("block_actions")) =>
          val actions: Many[Document] = payload.actions[Many[Document]]
          val action: DynDoc = actions.head
          val actionIdParts = action.action_id[String].split("-")
          val title = actionIdParts.last
          title match {
            case "ProjectItemEdit" =>
              val projectId = actionIdParts.init.init.last
              val itemName = actionIdParts.init.last
              val textInputField = SlackApi.createPlainTextInput(s"input-id", multiline = true)
              val inputBlock = SlackApi.createInputBlock(itemName,
                  s"block-id-$projectId-$itemName-EditProjectItem", textInputField)
              val modalView = SlackApi.createModalView(s"Edit Project Field",
                  s"modal-view-id-$itemName-EditProjectItem", Seq(inputBlock), withSubmitButton = true)
              SlackApi.viewPush(modalView.toJson, triggerId)
            case "ProjectDetail" =>
              val projectId = actionIdParts.init.last
              val sections = SlackInteractiveCallback.modalProjectDetail(projectId, request)
              val modalView = SlackApi.createModalView("Project Detail", "modal-view-id-ProjectDetail", sections)
              SlackApi.viewPush(modalView.toJson, triggerId)
            case "Projects" =>
              val sections = SlackInteractiveCallback.modalProjectList(bwUserRecord, request)
              val modalProjectsView = SlackApi.createModalView("Project List", "modal-view-id-ProjectList", sections)
              SlackApi.viewOpen(modalProjectsView.toJson, triggerId)
            case "IssueDetail" =>
              val issueId = actionIdParts.init.last
              val messageSections = SlackInteractiveCallback.modalIssueDetail(issueId, bwUserRecord, request)
              val modalView = SlackApi.createModalView("Issue Detail", "modal-view-id-IssueDetail", messageSections)
              SlackApi.viewPush(modalView.toJson, triggerId)
            case "Issues" =>
              val sections = SlackInteractiveCallback.modalIssueList(bwUserRecord, request)
              val modalIssuesView = SlackApi.createModalView("Issues List", "modal-view-id-Issues List", sections)
              SlackApi.viewOpen(modalIssuesView.toJson, triggerId)
            case _ =>
              val sections = Seq(SlackApi.createSection(s"The *$title* page is under construction.\nPlease check back later!"))
              val modalUnderConstructionView = SlackApi.createModalView(title, "modal-view-id-UnderConstruction", sections)
              SlackApi.viewOpen(modalUnderConstructionView.toJson, triggerId)
          }
        case (Some(triggerId), Some("view_submission")) =>
          val state: Document = payload.view[Document].y.state[Document]
          val returnedValues: Map[String, AnyRef] = state.y.values[Document].entrySet.asScala.
              map(es => (es.getKey, es.getValue)).toMap
          //val view: DynDoc = payload.view[Document]
          //val state: DynDoc = view.state[Document]
          //val stateJson = state.asDoc.toJson
          BWLogger.log(getClass.getName, request.getMethod, s"state: $returnedValues", request)

          if (returnedValues.head._1.matches("block-id-[0-9a-f]{24}-[^-]+-EditProjectItem")) {
            val projectItemInfo = returnedValues.head._1.split("-")
            val projectId = projectItemInfo.init.init.last
            val elementName = projectItemInfo.init.last.toLowerCase.replace(' ', '_')
            val elementValue = returnedValues.head._2.asInstanceOf[Document].y.`input-id`[Document].y.value[String]
            ProjectInfoSet.setProjectFields(projectId, Seq((elementName, elementValue)), request, doLog = true)
          } else if (returnedValues.keys.exists(_.matches("BW-root-tasks"))) {
            val taskViewMessage = SlackApi.createTaskSelectionView(bwUserRecord)
            val responseText = taskViewMessage.toJson
            BWLogger.log(getClass.getName, request.getMethod, s"response: $responseText", request)
            response.getWriter.println(responseText)
            response.setContentType("application/json")
          } else if (returnedValues.keys.exists(_.matches("BW-root-dashboard"))) {
            val dashboardViewMessage = SlackApi.createDashboardView(bwUserRecord)
            val responseText = dashboardViewMessage.toJson
            BWLogger.log(getClass.getName, request.getMethod, s"response: $responseText", request)
            response.getWriter.println(responseText)
            response.setContentType("application/json")
          } else if (returnedValues.keys.exists(_.matches("BW-tasks-update-display"))) {
//            val activityOid = new ObjectId(stateJson.split("-").last.substring(0, 24))
//            val theActivity = ActivityApi.activityById(activityOid)
//            val taskStatusUpdateViewMessage = SlackApi.createTaskStatusUpdateView(bwUserRecord, theActivity)
//            val responseText = taskStatusUpdateViewMessage.toJson
//            BWLogger.log(getClass.getName, request.getMethod, s"response: $responseText", request)
//            response.getWriter.println(responseText)
//            response.setContentType("application/json")
          } else if (returnedValues.keys.exists(_.matches("BW-tasks-update-completion-date"))) {
//            val values = state.values[Document]
//            val optimisticCompletionBlock = values.get("BW-tasks-update-completion-date-optimistic-block").asInstanceOf[Document]
//            val optimisticCompletionDatepicker = optimisticCompletionBlock.get("BW-tasks-update-completion-date-optimistic").asInstanceOf[Document]
//            val optimisticCompletionDate = optimisticCompletionDatepicker.getString("selected_date")
//            val pessimisticCompletionBlock = values.get("BW-tasks-update-completion-date-pessimistic-block").asInstanceOf[Document]
//            val pessimisticCompletionDatepicker = pessimisticCompletionBlock.get("BW-tasks-update-completion-date-pessimistic").asInstanceOf[Document]
//            val pessimisticCompletionDate = pessimisticCompletionDatepicker.getString("selected_date")
//            val likelyCompletionBlock = values.get("BW-tasks-update-completion-date-likely-block").asInstanceOf[Document]
//            val likelyCompletionDatepicker = likelyCompletionBlock.get("BW-tasks-update-completion-date-likely").asInstanceOf[Document]
//            val likelyCompletionDate = likelyCompletionDatepicker.getString("selected_date")
//            val percentCompleteBlock = values.get("BW-tasks-update-percent-complete-block").asInstanceOf[Document]
//            val percentCompleteInput = percentCompleteBlock.get("BW-tasks-update-percent-complete").asInstanceOf[Document]
//            val percentCompleteValue = percentCompleteInput.getString("value")
//            val completionCommentsBlock = values.get("BW-tasks-update-comments-block").asInstanceOf[Document]
//            val completionCommentsInput = completionCommentsBlock.get("BW-tasks-update-comments").asInstanceOf[Document]
//            val completionCommentsValue = completionCommentsInput.getString("value")
//            val message = s"optimistic: $optimisticCompletionDate, pessimistic: $pessimisticCompletionDate, " +
//              s"likely: $likelyCompletionDate, % complete: $percentCompleteValue, comments: $completionCommentsValue"
//            BWLogger.log(getClass.getName, request.getMethod, s"Received values: $message", request)
          } else {
          }
        case _ =>
      }
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object SlackInteractiveCallback {

  def modalProjectDetail(projectId: String, request: HttpServletRequest): Seq[DynDoc] = {
    val info: DynDoc = Document.parse(
        ProjectInfo.project2json(ProjectApi.projectById(new ObjectId(projectId)), request, doLog = true))
    val itemNames = Seq("Name", "Status", "Description", "Type", "Construction type",
        "Address line1", "Address line2", "Address line3", "Postal code", "State name", "Country name",
        "GPS latitude", "GPS longitude", "Budget MM USD", "Construction area SqFt", "Land area acres",
        "Max building height Ft")
    itemNames.map(itemName => {
      val itemContainer: DynDoc = info.get[Document](itemName.toLowerCase().replace(' ', '_')).get
      val itemValue = itemContainer.value[String]
      val itemEditable = itemContainer.editable[Boolean]
      val editButtonId = s"action-id-$projectId-$itemName-ProjectItemEdit"
      val editButtonValue = s"button-value-$projectId-$itemName-ProjectItemEdit"
      val editButton = SlackApi.createButton("Edit", editButtonId, editButtonId)
      if (itemEditable) {
        SlackApi.createSectionWithAccessory(s"*$itemName*: $itemValue", editButton)
      } else {
        SlackApi.createSection(s"*$itemName*: $itemValue")
      }
    })
  }

  def modalProjectList(bwUser: DynDoc, request: HttpServletRequest): Seq[DynDoc] = {
    val projects = ProjectList.getList(bwUser._id[ObjectId], request, doLog = true)
    projects.map(project => {
      val phaseCount = project.phase_ids[Many[ObjectId]].length
      val description = s"*${project.name[String]}*  (${project.status[String]})\nHas $phaseCount phases."
      val projectId = project._id[ObjectId].toString
      val buttonId = s"button-value-$projectId-ProjectDetail"
      val button = SlackApi.createButton("Detail", buttonId, buttonId)
      SlackApi.createSectionWithAccessory(description, button)
    })
  }

  def modalIssueDetail(issueId: String, user: DynDoc, request: HttpServletRequest): Seq[DynDoc] = {
    val messages = RfiDetails.getMessages(issueId, user, request, doLog = true)
    messages.map(message => {
      val messageText =
        s"""*Time:* ${message.timestamp[String]}, *Sender:* ${message.sender[String]}
           |${message.text[String]}""".stripMargin
      SlackApi.createSection(messageText)
    })
  }

  def modalIssueList(bwUser: DynDoc, request: HttpServletRequest): Seq[DynDoc] = {
    val issues: Seq[DynDoc] = RfiList.getRfiList(bwUser, Some(request), doLog = true)
    if (issues.nonEmpty) {
      issues.map(issue => {
        val startDate = issue.origination_date[String].split("\\s").head
        val priority = issue.priority[String]
        val originator = issue.originator[String]
        val subject = issue.subject[String]
        val question = issue.question[String]
        val state = issue.state[String]
        val sectionText =
          s"""*Date* $startDate *Priority* $priority *Subject* $subject *Question* $question *State* $state
             |*Originator* $originator""".stripMargin
        val issueId = issue._id[String]
        val buttonId = s"button-value-$issueId-IssueDetail"
        val button = SlackApi.createButton("Detail", buttonId, buttonId)
        SlackApi.createSectionWithAccessory(sectionText, button)
      })
    } else {
      Seq(SlackApi.createSection("You have no issues at this time")
      )
    }
  }

}

object SlackInteractiveCallbackTest extends App {

  val taskOptions = SlackApi.createSelectInputBlock("Select a task", "Select task", "BW-tasks",
    Seq(("Task A", "task-a"), ("Task B", "task-b"), ("Task C", "task-c"), ("Task D", "task-d")))
  val tasksModalView = SlackApi.createModalView("Select Task", "BW-tasks", Seq(taskOptions), withSubmitButton = true)
  val viewMessage: Document = Map("view" -> tasksModalView, "response_action" -> "push")
  val responseText = viewMessage.toJson

  println(responseText)
}
