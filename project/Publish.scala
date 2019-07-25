package akka.grpc

import sbt._, Keys._

object Publish extends AutoPlugin {
  import bintray.BintrayPlugin
  import bintray.BintrayPlugin.autoImport._

  override def trigger = allRequirements
  override def requires = BintrayPlugin

  override def projectSettings = Seq(bintrayOrganization := None, bintrayPackage := "akka-grpc", bintrayRepository := "maven-scratchpad")
}
