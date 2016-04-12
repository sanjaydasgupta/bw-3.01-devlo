package com.buildwhiz.etc

import javafx.application.Application
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.layout.StackPane
import javafx.stage.Stage

class DemoFx extends Application() {
  println("DemoFx-App")
  override def init(): Unit = {
    println("init()")
  }

  def start(primaryStage: Stage) {
    primaryStage.setTitle("Hello World!")
    val btn: Button = new Button
    btn.setText("Say 'Hello World'")
    btn.setOnAction(new EventHandler[ActionEvent]() {
      def handle(event: ActionEvent) {
        System.out.println("Hello World!")
      }
    })
    val root: StackPane = new StackPane
    root.getChildren.add(btn)
    primaryStage.setScene(new Scene(root, 300, 250))
    primaryStage.show()
  }

}

object DemoFx extends App {
  Application.launch(classOf[DemoFx], args: _*)
}
