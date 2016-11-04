import sbt.Keys._
import de.heikoseeberger.sbtheader.license.Apache2_0

lazy val artifactSettings = Seq(
  name := "sbt-microsites",
  organization := "com.fortysevendeg",
  organizationName := "47 Degrees",
  homepage := Option(url("http://www.47deg.com")),
  organizationHomepage := Some(new URL("http://47deg.com")),
  headers := Map(
    "scala" -> Apache2_0("2016", "47 Degrees, LLC. <http://www.47deg.com>")
  )
)

lazy val pluginSettings = Seq(
    sbtPlugin := true,
    scalaVersion in ThisBuild := "2.10.6",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases"),
      "jgit-repo" at "http://download.eclipse.org/jgit/maven"
    ),
    libraryDependencies ++= Seq(
      "com.lihaoyi"    %% "scalatags"  % "0.6.0",
      "org.scalactic"  %% "scalactic"  % "3.0.0",
      "org.scalatest"  %% "scalatest"  % "3.0.0" % "test",
      "org.scalacheck" %% "scalacheck" % "1.13.2" % "test"
    ),
    scalafmtConfig in ThisBuild := Some(file(".scalafmt"))
  ) ++ reformatOnCompileSettings

lazy val micrositeSettings = Seq(
  micrositeName := "sbt-microsites",
  micrositeDescription := "An sbt plugin to create awesome microsites for your project",
  micrositeBaseUrl := "sbt-microsites",
  micrositeDocumentationUrl := "/sbt-microsites/docs/",
  micrositeGithubOwner := "47deg",
  micrositeGithubRepo := "sbt-microsites",
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.md"
)

lazy val jsSettings = Seq(
  scalaVersion := "2.11.8",
  scalaJSStage in Global := FastOptStage,
  parallelExecution := false,
  scalaJSUseRhino := false,
  requiresDOM := false,
  jsEnv := NodeJSEnv().value,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.0",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.0",
    "com.lihaoyi" %%% "upickle" % "0.4.1",
    "org.scala-exercises" %%% "evaluator-client" % "0.1.2-SNAPSHOT",
    "com.lihaoyi" %%% "scalatags"  % "0.6.0",
    "org.querki" %%% "jquery-facade" % "1.0-RC6",
    "org.denigma" %%% "codemirror-facade" % "5.11-0.7",
    "com.fortysevendeg" %%% "github4s" % "0.8.2-SNAPSHOT",
    "fr.hmil" %%% "roshttp" % "2.0.0-RC1"
  ),
  resolvers ++= Seq(Resolver.url(
    "bintray-sbt-plugin-releases",
    url("https://dl.bintray.com/content/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayRepo("denigma", "denigma-releases")),
  jsDependencies ++= Seq(
    "org.webjars" % "jquery" % "2.1.3" / "2.1.3/jquery.js",
    ProvidedJS / "codemirror.js",
    ProvidedJS / "javascript.js" dependsOn "codemirror.js"
  )
)

lazy val testSettings =
  ScriptedPlugin.scriptedSettings ++ Seq(
    scriptedDependencies <<= (compile in Test) map { (analysis) =>
      Unit
    },
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq(
          "-Xmx2048M",
          "-XX:MaxPermSize=512M",
          "-XX:ReservedCodeCacheSize=256m",
          "-XX:+UseConcMarkSweepGC",
          "-Dplugin.version=" + version.value,
          "-Dscala.version=" + scalaVersion.value
        )
    }
  )

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "microsites"
)

lazy val miscSettings = Seq(
  shellPrompt := { s: State =>
    val c     = scala.Console
    val blue  = c.RESET + c.BLUE + c.BOLD
    val white = c.RESET + c.BOLD

    val projectName = Project.extract(s).currentProject.id

    s"$blue$projectName$white>${c.RESET}"
  }
)

lazy val commonSettings = artifactSettings ++ miscSettings
lazy val allSettings    = pluginSettings ++ commonSettings ++ tutSettings ++ testSettings

lazy val `sbt-microsites` = (project in file("."))
  .settings(moduleName := "sbt-microsites")
  .settings(allSettings: _*)
  .settings(addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.3"))
  .settings(addSbtPlugin("org.tpolecat"     % "tut-plugin"          % "0.4.4"))
  .settings(addSbtPlugin("com.typesafe.sbt" % "sbt-site"            % "1.0.0"))
  .settings(addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages"         % "0.5.4"))
  .enablePlugins(JekyllPlugin, AutomateHeaderPlugin)

lazy val docs = (project in file("docs"))
  .settings(commonSettings: _*)
  .settings(micrositeSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(buildInfoSettings: _*)
  .settings(moduleName := "docs")
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(BuildInfoPlugin)

lazy val js = (project in file("js"))
  .settings(moduleName := "sbt-microsites-js")
  .settings(commonSettings:_*)
  .settings(jsSettings:_*)
  .settings(KazariBuild.kazariTasksSettings:_*)
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey]("token" -> Option(sys.props("token")).getOrElse("")),
    buildInfoPackage := "kazari"
  )