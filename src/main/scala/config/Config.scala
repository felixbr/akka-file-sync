package config

import java.io.File

import com.typesafe.config.ConfigFactory

object Config {
  val config = ConfigFactory.parseFile(new File("app.conf"))
  println(config.getObject("app"))

  val folders = config.getObject("app.folders")
}
