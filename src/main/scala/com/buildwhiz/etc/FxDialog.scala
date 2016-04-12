package com.buildwhiz.etc

import javafx.application.Application
import javafx.scene.control.Alert
import javafx.scene.control.Alert._
import javafx.stage.Stage

class FxDialog extends Application {

  val alert = new Alert(AlertType.INFORMATION)

  def start(primaryStage: Stage): Unit = {
    alert.setTitle("Information Dialog")
    alert.setHeaderText(null)
    alert.setContentText("I have a great message for you!")
    alert.showAndWait()
  }

}

object FxDialog extends App {
  Application.launch(classOf[FxDialog], args: _*)
}
