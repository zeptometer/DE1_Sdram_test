lazy val root = (project in file(".")).settings(
  name := "sdramtest",
  version := "0.1.0",
  scalaVersion := "2.11.7"
).dependsOn(
  ProjectRef(uri("git://github.com/wasabiz/chisel-uart.git"), "chisel-uart"),
  ProjectRef(uri("git://github.com/zeptometer/chisel-DE1-SDRAM.git"), "chisel-de1-sdram"))

libraryDependencies += "edu.berkeley.cs" %% "chisel" % "latest.release"
