# akka-file-sync
A master-master file sync application using Akka

### Purpose and scope

Right now this is mainly used to try out all the greatness that is Akka. Once it's in a reasonable state I want to use it to backup data and keep certain folders in sync on multiple computers in my local network.

### Current state

* Detecting missing files and copying them over is implemented. Until there is a reasonable conflict resolution, files are only compared by name. This means deletion is not supported (files will be recovered atm) and file modification is not propagated.
* Automatic cluster discovery isn't implemented yet, but shouldn't be too hard.
* It's missing tests, as I'm mostly just hacking around at this point (yeah, I know). I will however start writing some when I get around to migrating to Akka-Typed. If you happen to know anything that helps mocking the file-system state/api, please tell me!

### Requirements

* An `app.conf` file with information about your nodes (right now there is only `path1` and `path2` used for local testing). A sample is provided.
* Scala 2.11.x and SBT 0.13.x

### Running it

    sbt run
