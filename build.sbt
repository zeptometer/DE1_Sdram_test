name := "chisel-uart"
version := "0.1.0"
scalaVersion := "2.11.7"

lazy val chiselUart  = uri("https://github.com/wasabiz/chisel-uart.git")
lazy val chiselSdram = uri("https://github.com/zeptometer/chisel-DE1-SDRAM.git")
lazy val root = project.in(file(".")).dependsOn(chiselUart).dependsOn(chiselSdram)

libraryDependencies += "edu.berkeley.cs" %% "chisel" % "latest.release"
