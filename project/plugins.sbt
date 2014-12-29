logLevel := Level.Warn

resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
                   Resolver.url("bintray-sbt-plugin-releases", url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))
                               (Resolver.ivyStylePatterns))


addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.7")

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")