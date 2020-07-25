package com.buildwhiz

package object infra {
  case class FileMetadata(key: String, size: Long, created: Long, lastModified: Long)
}
