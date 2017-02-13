package com.buildwhiz.infra

import java.io.File

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

object AmazonS3 {
  private def s3Client = new AmazonS3Client(new BasicAWSCredentials(
      "AKIAIM4CBPTFFQOLEA5Q", "9D+yOuDwHnAt2TLyot+2Mtm/4pC/bQiIdsFNe2Mu"))

  private val bucketName = "buildwhiz"

  def listObjects: ObjectListing = s3Client.listObjects(bucketName)
  def listObjects(prefix: String): ObjectListing = s3Client.listObjects(bucketName, prefix)

  def deleteObject(key: String): Unit = {} //s3Client.deleteObject(bucketName, key)

  def putObject(key: String, file: File): PutObjectResult = s3Client.putObject(bucketName, key, file)

  def getObject(key: String): S3Object = s3Client.getObject(bucketName, key)

  case class Summary(total: Int, orphans: Int, totalSize: Long, smallest: Long, biggest: Long, earliest: Long, latest: Long)

  def isOrphan(key: String): Boolean = {
    try {
      val parts = key.split("-")
      val (projOid, docOid, timestamp) = (new ObjectId(parts(0)), new ObjectId(parts(1)),
        java.lang.Long.parseLong(parts(2), 16))
      val doc: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> docOid, "project_id" -> projOid)).head
      val versions: Seq[DynDoc] = doc.versions[Many[Document]].asScala
      !versions.exists(_.timestamp[Long] == timestamp)
    } catch {
      case _: Throwable => true
    }
  }

  def listOrphans: Seq[String] = {
    val lor = new ListObjectsRequest()
    lor.setBucketName(bucketName)
    def once(acc: Seq[String] = Nil): Seq[String] = {
      val listing = s3Client.listObjects(lor)
      val keys = listing.getObjectSummaries.asScala.map(_.getKey)
      val orphans = keys.filter(isOrphan) ++ acc
      if (listing.isTruncated) {
        lor.setMarker(listing.getMarker)
        once(orphans)
      } else
        orphans
    }
    once()
  }

  def getSummary: Summary = {
    val lor = new ListObjectsRequest()
    lor.setBucketName(bucketName)
    def getStats(acc: Summary = Summary(0, 0, 0, Long.MaxValue, 0, Long.MaxValue, 0)): Summary = {
      def combine(sum: Summary, s3: S3ObjectSummary): Summary = {
        val key = s3.getKey
        val newOrphanCount = sum.orphans + (if (isOrphan(key)) 1 else 0)
        val size = s3.getSize
        val newTotalSize = size + sum.totalSize
        val total = sum.total + 1
        val millis = s3.getLastModified.getTime
        val earliest = math.min(millis, sum.earliest)
        val latest = math.max(millis, sum.latest)
        val smallest = math.min(size, sum.smallest)
        val biggest = math.max(size, sum.biggest)
        Summary(total, newOrphanCount, newTotalSize, smallest, biggest, earliest, latest)
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
    val orphans = listOrphans
    println(orphans.length)
    println(orphans)
  }
}