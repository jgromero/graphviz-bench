name := "drugbank-tests"

version := "0.1"

scalaVersion := "2.12.4"

resolvers += "NetBeans" at "http://bits.netbeans.org/nexus/content/groups/netbeans/"
resolvers += "gephi" at "https://raw.github.com/gephi/gephi/mvn-thirdparty-repo/"

libraryDependencies += "log4j" % "log4j" % "1.2.17"

libraryDependencies += "org.eclipse.rdf4j" % "rdf4j-runtime" % "2.2.4"
libraryDependencies += "org.eclipse.rdf4j" % "rdf4j-rio-jsonld" % "2.2.4" % "runtime"
libraryDependencies += "com.github.jsonld-java" % "jsonld-java" % "0.11.1"

libraryDependencies += "com.google.collections" % "google-collections" % "1.0"
libraryDependencies += "com.univocity" % "univocity-parsers" % "2.6.0"

libraryDependencies += "org.apache.tinkerpop" % "gremlin-core" % "3.3.1"
libraryDependencies += "org.apache.tinkerpop" % "tinkergraph-gremlin" % "3.3.1"
libraryDependencies += "com.tinkerpop.blueprints" % "blueprints-graph-jung" % "2.6.0"
libraryDependencies += "com.tinkerpop.blueprints" % "blueprints-core" % "2.6.0"
libraryDependencies += "net.sf.jung" % "jung-algorithms" % "2.1.1"

libraryDependencies += "org.gephi" % "gephi-toolkit" % "0.9.2" classifier "all"

