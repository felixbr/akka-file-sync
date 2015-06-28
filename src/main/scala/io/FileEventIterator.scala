package io

import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{FileSystems, Path, WatchEvent, WatchKey}

import domain._
import util._

import scala.collection.JavaConverters._
import scala.collection.mutable

object FileEventIterator {
  val watcher = FileSystems.getDefault.newWatchService()

  private[FileEventIterator] val watchKeys = mutable.Set[WatchKey]()
}

class FileEventIterator(dir: Path) extends Iterator[FileEvent] {
  val key: WatchKey = dir.register(FileEventIterator.watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
  FileEventIterator.watchKeys.add(key)

  val supportedEvents = Set(ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
  private val queue = mutable.Queue[WatchEvent[_]]()

  private var filesInDir = FolderUtils.filesInDir(dir.toString)

  override def next(): FileEvent = {
    if (queue.isEmpty) {
      pollNextEvents()
    }

    val nextEvent = queue.dequeue()
    if (isSupported(nextEvent)) {
      val fileEvent = watchEventToFileEvent(nextEvent)
      rescanFileInDir()

      fileEvent
    } else {
      next()
    }
  }

  override def hasNext: Boolean = true // YOLO

  def pollNextEvents() = {
    val key = FileEventIterator.watcher.take()
    queue.enqueue(key.pollEvents().asScala.toList: _*)

    val valid = key.reset()
    if (!valid) {
      FileEventIterator.watchKeys.remove(key)
    }
  }

  def watchEventToFileEvent(we: WatchEvent[_]): FileEvent = {
    val filePath = dir.resolve(we.context().toString)

    we.kind() match {
      case ENTRY_CREATE => FileEvent(FileCreated, filePath, FolderUtils.fileChecksum(filePath.toString))
      case ENTRY_MODIFY => FileEvent(FileChanged, filePath, FolderUtils.fileChecksum(filePath.toString))
      case ENTRY_DELETE => FileEvent(FileDeleted, filePath, lookupFileChecksum(filePath.toString))
    }
  }

  def rescanFileInDir() = filesInDir = FolderUtils.filesInDir(dir.toString)

  def lookupFileChecksum(filePath: String): String = filesInDir.find(_.absPath == filePath).map(_.checksum).getOrElse("")

  def isSupported(event: WatchEvent[_]) = supportedEvents.contains(event.kind().asInstanceOf[WatchEvent.Kind[Path]])
}
