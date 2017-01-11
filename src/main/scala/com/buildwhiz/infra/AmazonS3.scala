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

  case class Summary(count: Long, totalSize: Long, smallest: Long, biggest: Long, earliest: Long, latest: Long)

  def getSummary: Summary = {
    val lor = new ListObjectsRequest()
    lor.setBucketName(bucketName)
    def getStats(acc: Summary = Summary(0, 0, Long.MaxValue, 0, Long.MaxValue, 0)): Summary = {
      def combine(sum: Summary, s: S3ObjectSummary): Summary = {
        val size = s.getSize + sum.totalSize
        val count = sum.count + 1
        val ms = s.getLastModified.getTime
        val earliest = math.min(ms, sum.earliest)
        val latest = math.max(ms, sum.latest)
        val smallest = math.min(size, sum.smallest)
        val biggest = math.max(size, sum.biggest)
        Summary(count, size, smallest, biggest, earliest, latest)
      }
      val listing = s3Client.listObjects(lor)
      val summaries = listing.getObjectSummaries
      val newMap: Summary = summaries.asScala.foldLeft(acc)(combine)
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