package com.buildwhiz
import com.buildwhiz.infra.GoogleDriveRepository
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import com.buildwhiz.infra.DynDoc._

object ScratchPad extends App {
  val objects = GoogleDriveRepository.listObjects(None)
  print(objects.length)
}