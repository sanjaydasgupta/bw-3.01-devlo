package com.buildwhiz.infra

import java.io.File

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{ObjectListing, S3Object, PutObjectResult}

object AmazonS3 {
  private lazy val s3Client = new AmazonS3Client(new BasicAWSCredentials(
      "AKIAIM4CBPTFFQOLEA5Q", "9D+yOuDwHnAt2TLyot+2Mtm/4pC/bQiIdsFNe2Mu"))

  private val bucketName = "buildwhiz"

  def listObjects: ObjectListing = s3Client.listObjects(bucketName)
  def listObjects(prefix: String): ObjectListing = s3Client.listObjects(bucketName, prefix)

  def deleteObject(key: String) = s3Client.deleteObject(bucketName, key)

  def putObject(key: String, file: File): PutObjectResult = s3Client.putObject(bucketName, key, file)

  def getObject(key: String): S3Object = s3Client.getObject(bucketName, key)

  def main(args: Array[String]): Unit = {
    println(s3Client.getS3AccountOwner)
  }
}