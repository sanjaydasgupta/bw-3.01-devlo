package com.buildwhiz.infra

import java.io.File

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._

import scala.collection.JavaConverters._

object AmazonS3 {
  private lazy val s3Client = new AmazonS3Client(new BasicAWSCredentials(
      "AKIAIM4CBPTFFQOLEA5Q", "9D+yOuDwHnAt2TLyot+2Mtm/4pC/bQiIdsFNe2Mu"))

  private val bucketName = "buildwhiz"

  def listObjects: ObjectListing = s3Client.listObjects(bucketName)
  def listObjects(prefix: String): ObjectListing = s3Client.listObjects(bucketName, prefix)

  def deleteObject(key: String): Unit = {} //s3Client.deleteObject(bucketName, key)

  def putObject(key: String, file: File): PutObjectResult = s3Client.putObject(bucketName, key, file)

  def getObject(key: String): S3Object = s3Client.getObject(bucketName, key)

  def getSummary: Map[String, Long] = {
    val lor = new ListObjectsRequest()
    lor.setBucketName(bucketName)
    def getStats(acc: Map[String, Long] = Map("size" -> 0L, "earliest" -> Long.MaxValue, "latest" -> 0L, "count" -> 0L)): Map[String, Long] = {
      def combine(map: Map[String, Long], s: S3ObjectSummary): Map[String, Long] = {
        val size = s.getSize + map("size")
        val count = map("count") + 1
        val ms = s.getLastModified.getTime
        val earliest = math.min(ms, map("earliest"))
        val latest = math.max(ms, map("latest"))
        Map("size" -> size, "earliest" -> earliest, "latest" -> latest, "count" -> count)
      }
      val listing = s3Client.listObjects(lor)
      val summaries = listing.getObjectSummaries
      val newMap: Map[String, Long] = summaries.asScala.foldLeft(acc)(combine)
      if (listing.isTruncated) {
        lor.setMarker(listing.getMarker)
        getStats(newMap)
      }
      newMap
    }
    getStats()
  }

  def main(args: Array[String]): Unit = {
    println(getSummary)
  }
}