package actors

import java.util.UUID

import actors.FileTransmittingActor.StartListening
import actors.FolderActor._
import actors.RemoteCommunicationActor._
import akka.actor._
import akka.pattern.ask
import akka.remote.RemoteScope
import akka.util.Timeout
import domain.Types.{FolderContent, FolderId, FolderPathAbs}

import scala.concurrent.duration._

case class TransferFeedback(bytesSent: Long, totalBytes: Long)

object RemoteCommunicationActor {
  def props = Props(new RemoteCommunicationActor)

  sealed trait Query
  case class FolderFromId(folderId: FolderId) extends Query

  sealed trait Answer
  case class FolderPath(dirPath: FolderPathAbs) extends Answer

  sealed trait Command
  case class WatchFolders(folders: Map[FolderId, FolderPathAbs]) extends Command
  case class BroadcastFileEvent(event: SyncEvent) extends Command

  sealed trait SyncEvent
  case class FileCreated(location: Address, folderId: String, fileName: String, checksum: String) extends SyncEvent
  case class FolderChanged(location: Address, folderId: String)
  case class FilesNeededFromRemote(comHub: String, location: Address, folderId: String, files: FolderContent) extends SyncEvent
}

class RemoteCommunicationActor extends Actor with ActorLogging {
  val comHub = self.path.name

  private var folderActors = Map[FolderId, (FolderPathAbs, ActorRef)]()

  override def receive: Receive = {
    case WatchFolders(fs) => fs.foreach { case (id, folder) =>
      val ref = context.actorOf(FolderActor.props(id, folder), FolderActor.nameFromFolderId(id))
      folderActors = folderActors + (id -> Tuple2(folder, ref))

      log.info(s"currently watching: $folderActors")
    }

    case FilesInFolder(remoteComHub, id, remoteFileChecksums) =>
      log.debug(s"received FilesInFolder $remoteFileChecksums from ${sender().actorRef}")

      log.debug("telling folder actor to check diff")
      folderActors.get(id).foreach(_._2 ! CheckFolderDiff(remoteComHub, remoteFileChecksums))


    case f: FilesNeededFromRemote =>
      log.debug(s"FilesNeededFromRemote received on ${self.actorRef}")
      startTransferingRemoteFileToLocal(f)

    case FolderFromId(id) =>
      folderActors.get(id).foreach { case (path, _) =>
        log.debug(s"answering FolderPath to ${sender()}")
        sender() ! FolderPath(path)
      }

    case BroadcastFileEvent(fileEvent) =>
      log.debug(s"broadcasting event: $fileEvent")

      val coms = context.actorSelection("akka://filesync/user/com*")
      coms ! fileEvent
  }

  def startTransferingRemoteFileToLocal(f: FilesNeededFromRemote): Unit = {
    implicit val timeout = Timeout(3.seconds)
    log.debug(s"files needed remote: $f")

    f.files.foreach { fileChecksum =>
      log.debug(s"Instantiating actors for file transmission for $fileChecksum")
      val receiverActor = context.actorOf(FileReceivingActor.props(f.folderId, fileChecksum.relPath, comHub), s"${UUID.randomUUID()}-receiver")
      val transmitterActor = context.actorOf(FileTransmittingActor.props(f.folderId, fileChecksum.relPath, f.comHub)
                                                                  .withDeploy(Deploy(scope = RemoteScope(f.location))), s"${UUID.randomUUID()}-transmitter")

      transmitterActor ! StartListening(receiverActor)
    }
  }

  def isActorManagedHere(actorRef: ActorRef): Boolean = folderActors.values.toSeq.exists { case (_, ref) => ref == actorRef }
}