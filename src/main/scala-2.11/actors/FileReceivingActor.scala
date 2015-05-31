package actors

import java.io.{FileOutputStream, File}
import java.net.InetSocketAddress

import actors.FileTransmittingActor.Listening
import akka.actor._
import akka.event.LoggingReceive
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.remote.RemoteScope
import akka.stream.scaladsl._
import akka.util.ByteString
import mixins.FolderLookup

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._


object FileReceivingActor {
  def props(folderId: String, relPath: String, comHub: String) = Props(new FileReceivingActor(folderId, relPath, comHub: String))
}

class FileReceivingActor(folderId: String, relPath: String, comHub: String) extends Actor with ActorLogging with FolderLookup {
  implicit val system = context.system

  val file: Future[File] = queryForLocalFolder(folderId, comHub).map { folderPath =>
    new File(new File(folderPath).toPath.resolve(relPath).toString)
  }
  lazy val fileOutStream = new FileOutputStream(Await.result(file, 3.seconds))

  private var fileSize: Option[Long] = None
  private var bytesReceived = 0

  override def receive: Receive = {
    case Listening(host, port, length) =>
      log.info(s"trying to connect to $host:$port")
      IO(Tcp) ! Connect(new InetSocketAddress(host, port))
      fileSize = Some(length)

    case CommandFailed(_: Connect) => context stop self

    case Connected(remote, local) =>
      log.debug("connected from client")

      val connection = sender()
      connection ! Register(self)

      context become {
        case data: ByteString =>
          log.debug(s"received bytes: ${ByteString.toString}")

        case Received(data) =>
          log.debug(s"received data: $data")
          val bytes: Array[Byte] = data.toArray

          fileOutStream.write(bytes)
          fileOutStream.flush()

          bytesReceived += bytes.length
          fileSize.foreach { size =>
            log.debug(s"checking if transmition is complete: $bytesReceived of $size received")
            if (bytesReceived >= size) {
              log.debug(s"closing connection client")
              IO(Tcp) ! Tcp.ConfirmedClose
              fileOutStream.close()
            }
          }

        case CommandFailed(w: Write) =>
          log.error(s"write failed: ${w.toString}")
          fileOutStream.close()

        case _: ConnectionClosed =>
          log.debug(s"connection closed")
          fileOutStream.flush()
          fileOutStream.close()
          context stop self

        case a => log.debug(a.toString)
      }
  }
}
