import actors.RemoteCommunicationActor
import actors.RemoteCommunicationActor.WatchFolders
import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl._
import config.Config
import domain.{FileCreated, FileEvent}

object FileSyncApplication extends App {
  implicit lazy val system = ActorSystem("filesync")
  implicit lazy val mat = ActorFlowMaterializer()

  val path1 = Config.config.getString("app.path1")
  val path2 = Config.config.getString("app.path2")

  val comActor = system.actorOf(RemoteCommunicationActor.props, "com1")
  comActor ! WatchFolders(Map("f1" -> path1))

  val comActor2 = system.actorOf(RemoteCommunicationActor.props, "com2")
  comActor2 ! WatchFolders(Map("f1" -> path2))


  //  fileEventSource(path1).to(fileEventSink(Config.folders.values.head))
  //  fileEventSource(path1)
  //    .runForeach(printFileEvent)
  //    .onComplete(_ => system.shutdown())


  def fileEventSink = Sink.foreach[FileEvent] { event =>
    util.printFileEvent(event)
    event.eventType match {
      case FileCreated =>
      case _ => println(s"event ignored: ${event.eventType}")
    }
  }
}