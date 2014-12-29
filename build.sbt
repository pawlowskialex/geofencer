name := "GeoFencer"

version := "1.0"

lazy val `geofencer` = (project in file("."))
                       .enablePlugins(PlayScala)
                       .settings(bintrayResolverSettings: _*)
                       .settings(Revolver.settings: _*)

scalaVersion := "2.11.4"

val breezeVer: String = "0.8.1"
val sparkVer: String = "1.2.0"
val slf4jVer: String = "1.7.7"
val chillVersion: String = "0.5.1"

libraryDependencies ++= Seq(jdbc, anorm, cache, ws,
                            "org.slf4j" % "slf4j-api" % slf4jVer,
                            "org.slf4j" % "jcl-over-slf4j" % slf4jVer,
                            "org.slf4j" % "log4j-over-slf4j" % slf4jVer,
                            "org.clapper" %% "grizzled-slf4j" % "1.0.2",
                            "ch.qos.logback" % "logback-classic" % "1.1.2",
                            "commons-io" % "commons-io" % "2.4",
                            "org.apache.commons" % "commons-pool2" % "2.2",
                            "org.scalaz" %% "scalaz-core" % "7.1.0",
                            "org.scalaz.stream" %% "scalaz-stream" % "0.6a",
                            "net.ceedubs" %% "ficus" % "1.1.1",
                            "com.twitter" %% "bijection-core" % "0.7.0",
                            "com.twitter" %% "chill" % chillVersion,
                            "com.twitter" %% "chill-bijection" % chillVersion,
                            "org.scalanlp" %% "breeze" % breezeVer,
                            "org.scalanlp" %% "breeze-natives" % breezeVer,
                            "com.github.nscala-time" %% "nscala-time" % "1.6.0",
                            "org.apache.spark" %% "spark-core" % sparkVer,
                            "org.apache.spark" %% "spark-streaming" % sparkVer,
                            "org.apache.spark" %% "spark-mllib" % sparkVer,
                            "org.apache.kafka" %% "kafka" % "0.8.2-beta",
                            "com.101tec" % "zkclient" % "0.4",
                            "org.apache.zookeeper" % "zookeeper" % "3.3.4",
                            "com.sclasen" %% "akka-kafka" % "0.0.9-0.8.2-beta-SNAPSHOT",
//                            "org.apache.spark" % "spark-streaming-kafka_2.10" % sparkVer
//                              exclude("org.apache.kafka", "kafka_2.10")
//                              exclude("org.apache.spark", "spark-streaming_2.10")
//                              exclude("org.scalacheck", "scalacheck_2.10")
//                              exclude("org.scalatest", "scalatest_2.10")
                            "javax.servlet" % "javax.servlet-api" % "3.1.0",
                            "de.javakaffee" % "kryo-serializers" % "0.27")
                        .map(_.excludeAll(ExclusionRule(name = "jms"),
                                          ExclusionRule(name = "jmxri"),
                                          ExclusionRule(name = "jmxtools"),
                                          ExclusionRule(name = "log4j"),
                                          ExclusionRule(name = "slf4j-log4j12"),
                                          ExclusionRule(name = "slf4j-simple"),
                                          ExclusionRule(name = "jul-to-slf4j"))
                             .withSources())

mainClass in Compile := Some("ProdNettyServer")

mainClass in (Compile, run) := Some("DevNettyServer")

resolvers ++= Seq("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
                  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases")

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

