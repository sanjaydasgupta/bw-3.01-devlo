package com.buildwhiz.infra

import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId
import java.io.{InputStream, InputStreamReader}

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.DriveScopes
import java.util.Collections

import scala.collection.JavaConverters._
import java.io.{File => javaFile, FileInputStream}

import scala.annotation.tailrec
import scala.collection.JavaConverters._

object GoogleDrive {
  private val jsonFactory = JacksonFactory.getDefaultInstance
  private val SCOPES = Collections.singletonList(DriveScopes.DRIVE_READONLY)

  private def getCredentials(httpTransport: NetHttpTransport): Credential = {
    BWLogger.log(getClass.getName, "getCredentials()", s"ENTRY")
    val tomcatDir = new javaFile("server").listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val doNotTouchFolder = new javaFile(tomcatDir, "webapps/bw-dot-2.01/WEB-INF/classes/do-not-touch")
    if (!doNotTouchFolder.exists()) {
      throw new IllegalArgumentException(s"No such file: '${doNotTouchFolder.getAbsolutePath}'")
    }
    val credentialsFile = new javaFile(doNotTouchFolder, "drive-storage-demo.json")
    if (!credentialsFile.exists()) {
      throw new IllegalArgumentException(s"No such file: '${credentialsFile.getAbsolutePath}'")
    }
    val tokensFile = new javaFile(doNotTouchFolder, "tokens")
    if (!tokensFile.exists() || !tokensFile.isDirectory) {
      throw new IllegalArgumentException(s"No such directory: '${tokensFile.getAbsolutePath}'")
    }
    val credentialStream = new FileInputStream(credentialsFile)
    val clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(credentialStream))
    //respWriter.println("Got Client-Secrets")
    val builder = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, SCOPES)
    //respWriter.println("Got Builder")
    //respWriter.println(s"Tokens file exists: $tokensFileExists")
    val flow = builder.setDataStoreFactory(new FileDataStoreFactory(tokensFile)).setAccessType("offline").build()
    //respWriter.println("Got Flow")
    val receiver = new LocalServerReceiver.Builder().setPort(8888).build()
    //respWriter.println("Got Receiver")
    val credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    BWLogger.log(getClass.getName, "getCredentials()", s"EXIT")
    credential
  }

  private def driveService(): Drive = {
    BWLogger.log(getClass.getName, "driveService()", s"ENTRY")
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val drive = new Drive.Builder(httpTransport, jsonFactory, getCredentials(httpTransport)).
      setApplicationName("BuildWhiz-Connector").build()
    BWLogger.log(getClass.getName, "driveService()", s"EXIT")
    drive
  }

  BWLogger.log(getClass.getName, "Evaluating 'cachedDriveService'", s"ENTRY")
  private val cachedDriveService: Drive = driveService()
  BWLogger.log(getClass.getName, "Evaluating 'cachedDriveService'", s"EXIT")

  // https://developers.google.com/drive/api/v3/reference/files/list
  // https://developers.google.com/drive/api/v3/ref-search-terms
  // https://developers.google.com/drive/api/v3/reference/files

  def listObjects: Seq[FileMetadata] = {
    BWLogger.log(getClass.getName, "listObjects()", s"ENTRY")
    val result = cachedDriveService.files().list().setPageSize(10).
        setFields("nextPageToken, files(id, name, size, mimeType, createdTime, modifiedTime)").
        setQ("\'12vpPmRRS750v1chrr3z7E0jyd8jcCZvi\' in parents").execute()
    val files: Seq[File] = result.getFiles.iterator().asScala.toSeq
    val objects = files.map(file => FileMetadata(file.getName, file.getSize, file.getMimeType,
        file.getCreatedTime.getValue, file.getModifiedTime.getValue))
    BWLogger.log(getClass.getName, "listObjects()", s"EXIT")
    objects
  }

  def listObjects(prefix: String): Seq[FileMetadata] = listObjects
//
//  def deleteObject(key: String): Unit = {} //s3Client.deleteObject(bucketName, key)
//
  def putObject(key: String, file: File): Unit = {
  BWLogger.log(getClass.getName, "putObject()", s"ENTRY")
  BWLogger.log(getClass.getName, "putObject()", s"EXIT")
  }
//
  def getObject(key: String): InputStream = {
    BWLogger.log(getClass.getName, "putObject()", s"ENTRY")
    val in: InputStream = ???
    BWLogger.log(getClass.getName, "putObject()", s"EXIT")
    in
  }
//
//  case class Summary(total: Int, orphans: Int, totalSize: Long, smallest: Long, biggest: Long, earliest: Long, latest: Long)
//
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
//
//  def listOrphans: Seq[String] = {
//    val lor = new ListObjectsV2Request()
//    lor.setRequestCredentialsProvider(credentialsProvider)
//    lor.setBucketName(bucketName)
//    @tailrec
//    def once(acc: Seq[String] = Nil): Seq[String] = {
//      val listing = s3Client.listObjectsV2(lor)
//      val keys = listing.getObjectSummaries.asScala.map(_.getKey)
//      val orphans = keys.filter(isOrphan) ++ acc
//      if (listing.isTruncated) {
//        lor.setContinuationToken(listing.getContinuationToken)
//        once(orphans)
//      } else
//        orphans
//    }
//    once()
//  }
//
//  def getSummary: Summary = {
//    val lor = new ListObjectsV2Request()
//    lor.setRequestCredentialsProvider(credentialsProvider)
//    lor.setBucketName(bucketName)
//    def getStats(acc: Summary = Summary(0, 0, 0, Long.MaxValue, 0, Long.MaxValue, 0)): Summary = {
//      def combine(sum: Summary, s3: S3ObjectSummary): Summary = {
//        val key = s3.getKey
//        val newOrphanCount = sum.orphans + (if (isOrphan(key)) 1 else 0)
//        val size = s3.getSize
//        val newTotalSize = size + sum.totalSize
//        val total = sum.total + 1
//        val millis = s3.getLastModified.getTime
//        val earliest = math.min(millis, sum.earliest)
//        val latest = math.max(millis, sum.latest)
//        val smallest = math.min(size, sum.smallest)
//        val biggest = math.max(size, sum.biggest)
//        Summary(total, newOrphanCount, newTotalSize, smallest, biggest, earliest, latest)
//      }
//      val listing = s3Client.listObjectsV2(lor)
//      val summaries = listing.getObjectSummaries
//      val newMap: Summary = summaries.asScala.foldLeft(acc)(combine)
//      if (listing.isTruncated) {
//        lor.setContinuationToken(listing.getContinuationToken)
//        getStats(newMap)
//      }
//      newMap
//    }
//    getStats()
//  }
//
//  def main(args: Array[String]): Unit = {
//    val orphans = listOrphans
//    println(orphans.length)
//    println(orphans)
//  }
}