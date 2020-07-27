package com.buildwhiz

package object infra {
  case class FileMetadata(key: String, size: Long, mimeType: String = null,
      createdTime: Long = 0, modifiedTime: Long = 0)
}
