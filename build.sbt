name := "sdramtest"
version := "0.1.0"
scalaVersion := "2.11.7"

lazy val root = project.in(file(".")).dependsOn(chiselUart, chiselSdram)

lazy val chiselUart  = uri("git://github.com/wasabiz/chisel-uart.git")
lazy val chiselSdram = uri("git://github.com/zeptometer/chisel-DE1-SDRAM.git")

libraryDependencies += "edu.berkeley.cs" %% "chisel" % "latest.release"
