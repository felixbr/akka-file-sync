import actors.RemoteCommunicationActor
import actors.RemoteCommunicationActor.WatchFolders
import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import config.Config

object FileSyncApplication extends App {
  implicit lazy val system = ActorSystem("filesync")
  implicit lazy val mat = ActorFlowMaterializer()

  val path1 = Config.config.getString("app.path1")
  val path2 = Config.config.getString("app.path2")

  // start the communication actor on each network node
  val comActor = system.actorOf(RemoteCommunicationActor.props, "com1")
  comActor ! WatchFolders(Map("f1" -> path1))

  val comActor2 = system.actorOf(RemoteCommunicationActor.props, "com2")
  comActor2 ! WatchFolders(Map("f1" -> path2))
}