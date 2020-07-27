package com.buildwhiz.infra

import java.io.{File, InputStream}

import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider}
import com.buildwhiz.infra.DynDoc._
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model._
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConverters._

object AmazonS3 {
  private val instanceInfo: DynDoc = BWMongoDB3.instance_info.find().head
  private val awsSecret = instanceInfo.aws_secret[String]
  private object credentialsProvider extends AWSCredentialsProvider {
    object credentials extends AWSCredentials {
      override def getAWSAccessKeyId: String = "AKIAIM4CBPTFFQOLEA5Q"
      override def getAWSSecretKey: String = awsSecret
    }
    override def getCredentials: AWSCredentials = credentials
    override def refresh(): Unit = {}
  }
  private lazy val amazonS3ClientBuilder = AmazonS3ClientBuilder.standard()
  amazonS3ClientBuilder.setCredentials(credentialsProvider)
  private def s3Client = amazonS3ClientBuilder.withRegion(Regions.DEFAULT_REGION).build()

  private val bucketName = "buildwhiz"

  def listObjects: Seq[FileMetadata] = s3Client.listObjectsV2(bucketName).getObjectSummaries.
      map(file => FileMetadata(file.getKey, file.getSize, modifiedTime = file.getLastModified.getTime))
  def listObjects(prefix: String): Seq[FileMetadata] = s3Client.listObjectsV2(bucketName, prefix).getObjectSummaries.
    map(file => FileMetadata(file.getKey, file.getSize, modifiedTime = file.getLastModified.getTime))

  def deleteObject(key: String): Unit = {} //s3Client.deleteObject(bucketName, key)

  def putObject(key: String, file: File): Long = s3Client.putObject(bucketName, key, file).getMetadata.getContentLength

  def getObject(key: String): InputStream = s3Client.getObject(bucketName, key).getObjectContent

  case class Summary(total: Int, orphans: Int, totalSize: Long, smallest: Long, biggest: Long, earliest: Long, latest: Long)

  def isOrphan(key: String): Boolean = {
    try {
      val parts = key.split("-")
      val (projOid, docOid, timestamp) = (new ObjectId(parts(0)), new ObjectId(parts(1)),
        java.lang.Long.parseLong(parts(2), 16))
      val doc: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> docOid, "project_id" -> projOid)).head
      val versions: Seq[DynDoc] = doc.versions[Many[Document]]
      !versions.exists(_.timestamp[Long] == timestamp)
    } catch {
      case _: Throwable => true
    }
  }

  def listOrphans: Seq[String] = {
    val lor = new ListObjectsV2Request()
    lor.setRequestCredentialsProvider(credentialsProvider)
    lor.setBucketName(bucketName)
    @tailrec
    def once(acc: Seq[String] = Nil): Seq[String] = {
      val listing = s3Client.listObjectsV2(lor)
      val keys = listing.getObjectSummaries.asScala.map(_.getKey)
      val orphans = keys.filter(isOrphan) ++ acc
      if (listing.isTruncated) {
        lor.setContinuationToken(listing.getContinuationToken)
        once(orphans)
      } else
        orphans
    }
    once()
  }

  def getSummary: Summary = {
    val lor = new ListObjectsV2Request()
    lor.setRequestCredentialsProvider(credentialsProvider)
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
      val listing = s3Client.listObjectsV2(lor)
      val summaries = listing.getObjectSummaries
      val newMap: Summary = summaries.asScala.foldLeft(acc)(combine)
      if (listing.isTruncated) {
        lor.setContinuationToken(listing.getContinuationToken)
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