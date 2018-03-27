name := "graph-benchmark"

version := "0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.2.0" % "provided",
  "org.apache.spark" %% "spark-graphx" % "2.2.0",
  "org.apache.spark" %% "spark-sql" % "2.2.0",
  "org.json4s" %% "json4s-jackson" % "3.5.3",
  "com.univocity" % "univocity-parsers" % "2.5.9"
)