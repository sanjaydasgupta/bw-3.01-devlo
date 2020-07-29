package com.buildwhiz

import com.google.api.services.drive.model.File

package object infra {
  case class FileMetadata(key: String, size: Long, mimeType: String = null,
      createdTime: Long = 0, modifiedTime: Long = 0)
  object FileMetadata {
    def fromFile(file: File): FileMetadata = {
      val name = file.getName
      val size = file.getSize
      val mimeType = file.getMimeType
      val modifiedTime = file.getModifiedTime
      val createdTime = file.getCreatedTime
      FileMetadata(name, if (size == null) -1 else size, mimeType,
          if (createdTime == null) 0 else createdTime.getValue,
          if (modifiedTime == null) 0 else modifiedTime.getValue)
    }
  }
}
