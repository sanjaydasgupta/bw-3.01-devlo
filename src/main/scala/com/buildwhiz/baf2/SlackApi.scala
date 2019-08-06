package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc

object SlackApi {

  def name(person: DynDoc): String = s"${person.first_name} ${person.last_name}"

  def invite(who: DynDoc): DynDoc => String = {
    user => {
      s"${name(user)} wishes to invite ${name(who)}"
    }
  }
  def status(who: DynDoc): DynDoc => String = {
    user => {
      val slackStatus = if (who.has("slack_id"))
        "Connected"
      else
        "Not connected"
      s"${name(who)}'s Slack status is '$slackStatus'"
    }
  }

}
