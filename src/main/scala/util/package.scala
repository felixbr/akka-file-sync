import java.nio.file.WatchEvent

import domain.FileEvent

package object util {
  def printFileEvent(event: FileEvent) = println(s"${event.eventType}: ${event.filePath} (${event.checksum})\n")

  def printWatchEvent(event: WatchEvent[_]) = println(s"${event.hashCode()} -> ${event.kind()}: ${event.context()}\n")
}
