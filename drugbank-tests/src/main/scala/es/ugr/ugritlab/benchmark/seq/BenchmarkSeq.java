package es.ugr.ugritlab.benchmark.seq;

import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.oupls.jung.GraphJung;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import es.ugr.ugritlab.es.ugr.ugritlab.graphs.gephi.GephiLayoutManager;
import es.ugr.ugritlab.es.ugr.ugritlab.graphs.rdf.RDFGraphDL;
import org.apache.commons.io.IOUtils;
import org.apache.tinkerpop.gremlin.structure.io.IoCore;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.gephi.io.importer.api.EdgeDirectionDefault;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Main class for the sequential benchmark with the DrugBank dataset.
 *
 * <p>Set {@link BenchmarkSeq#sparqlEndpoint} field to connecto to the SPARQL endpoint.</p>
 *
 * <p>The program accepts one argument: the LIMIT value for the number of drug interactions to retrieve. if
 * not specified or negative, the LIMIT is set to 0 --i.e. no limit.</p>
 *
 * <p>The program generates several files:</p>
 * <ul>
 *     <li>files/drugbank/seq/results/[uniqueID]/results.csv: performance statistics</li>
 *     <li>files/drugbank/triples/triples_[limit].ttl: retrieved triples file, represented with <a href="https://github.com/jgromero/graphdl">GraphDL</a></li>
 *     <li>files/drugbank/seq/results/[uniqueID]/*: temporal Gephi files</li>
 *     <li>files/drugbank/seq/results/[uniqueID]/edges_[limit].txt: text file with edge pairs with format [vertex id] [edge id]</li>
 *     <li>files/drugbank/seq/results/[uniqueID]/tinkergraph.xml: TinkerPop temporal file</li>
 *     <li>files/drugbank/seq/results/[uniqueID]/graph-layout.graphml: GraphML file after layout</li>
 * </ul>
 *
 * @author Juan Gomez Romero
 * @version 0.2
 */
public class BenchmarkSeq {

    public static final String sparqlEndpoint = ""; // set endpoint URL

    public static void main(String[] args) throws Exception {
        String ID = String.valueOf(Instant.now().toEpochMilli());

        /* Get limit for the number of drugs considered */
        int limit = 0;
        if(args.length > 0) {
            limit = Integer.parseInt(args[0]);
            if(limit <= 0)
                limit = 0;
        }

        System.out.println("Running... limit=" + limit);

        /* Create statistics file */
        String stats_file = "files/drugbank/seq/results/" + ID + "/results.csv";
        CsvWriter writer_head = new CsvWriter(new File(stats_file), new CsvWriterSettings());
        writer_head.writeHeaders("limit", "number.of.vertices", "number.of.edges", "density", "query.time", "build.time", "pagerank.time", "layout.time", "output.graph.file.name");
        writer_head.close();
        ArrayList<String> stats = new ArrayList<>();

        /* Query remote repository & write to file */
        String queryString = readFile("files/drugbank/select.sparql", Charset.defaultCharset());    // SELECT is used to avoid CONSTRUCT error with Virtuoso triplestore. @todo Replace with CONSTRUCT
        queryString = queryString.replace("DRUG_LIMIT_CLAUSE", limit>0? "LIMIT " + limit : "");
        queryString = queryString.replace("DRUG_ORDER_CLAUSE", "");

        SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint);
        repo.initialize();
        repo.enableQuadMode(true);
        RepositoryConnection con = repo.getConnection();

        ModelBuilder builder = new ModelBuilder();
        builder.setNamespace("drugbank", "http://bio2rdf.org/drugbank_vocabulary:")
               .setNamespace("graphdl", "http://ugritlab.ugr.es/graphdl#")
               .setNamespace("dcterms", "http://purl.org/dc/terms/");

        long t_startQuery = Instant.now().toEpochMilli();
        TupleQuery query = con.prepareTupleQuery(queryString);
        try (TupleQueryResult result = query.evaluate()) {
            int nTriples = 0;
            while (result.hasNext()) {
                BindingSet solution = result.next();
                builder.subject(solution.getValue("d1").stringValue())
                            .add(RDF.TYPE, "graphdl:Node")
                       .subject(solution.getValue("d2").stringValue())
                            .add(RDF.TYPE, "graphdl:Node")
                       .subject(solution.getValue("i").stringValue())
                            .add(RDF.TYPE, "graphdl:Edge")
                            .add("graphdl:source", solution.getValue("d1"))
                            .add("graphdl:target", solution.getValue("d2"));
                nTriples++;
            }
        }
        Model model = builder.build();
        long t_endQuery = Instant.now().toEpochMilli();

        // Write triples to folder
        String triplesFolder = "files/drugbank/triples/";
        long t_startWrite = Instant.now().toEpochMilli();
        PrintWriter writer1 = new PrintWriter(triplesFolder + "triples_" + limit + ".ttl", "UTF-8");
        for(Statement stmt : model) {  // iterate over the result
            writer1.write(stmt.getSubject().toString() + " ");
            writer1.write(stmt.getPredicate().toString() + " ");
            writer1.write(stmt.getObject().toString() + "\n");
        }
        writer1.close();
        long t_endWrite = Instant.now().toEpochMilli();

        con.close();

        /* Transform GraphDL triples into a graph structure */
        long t_startBuild = Instant.now().toEpochMilli();
        RDFGraphDL graph = new RDFGraphDL();
        graph.load(model);
        long t_endBuild = Instant.now().toEpochMilli();

        /* Layout graph with Gephi */
        graph.cleanForGraphML();
        TinkerGraph tinker = graph.asTinkerGraph();
        long t_startLayout = Instant.now().toEpochMilli();
        GephiLayoutManager lm = new GephiLayoutManager(ID, "files/drugbank/seq/results/" + ID + "/" );
        lm.init();
        TinkerGraph tinkerLayout = lm.doLayout(tinker, null);
        long t_endLayout = Instant.now().toEpochMilli();

        // graph .txt
        PrintWriter writer2 = new PrintWriter("files/drugbank/seq/results/" + ID + "/edges_" + limit + ".txt", "UTF-8");
        Iterator<org.apache.tinkerpop.gremlin.structure.Edge> edges = tinker.edges();
        while(edges.hasNext()) {  // iterate over the result
            org.apache.tinkerpop.gremlin.structure.Edge e = edges.next();
            writer2.write(e.inVertex().id().toString());
            writer2.write(" ");
            writer2.write(e.outVertex().id().toString());
            writer2.write("\n");
        }
        writer2.close();

        /* Calculate PageRank */
        String tmpPageRankFolder = "files/drugbank/seq/results/" + ID + "/";
        File tmpPageRankFile = new File("files/drugbank/seq/results/" + ID + "/tinkergraph.xml");
        tmpPageRankFile.deleteOnExit();
        tinker.io(IoCore.graphml()).writeGraph(tmpPageRankFile.getAbsolutePath());
        com.tinkerpop.blueprints.impls.tg.TinkerGraph tinkerBlueprints =
                new com.tinkerpop.blueprints.impls.tg.TinkerGraph(
                        tmpPageRankFolder,
                        com.tinkerpop.blueprints.impls.tg.TinkerGraph.FileType.GRAPHML);
        long t_startPagerank = Instant.now().toEpochMilli();
        GraphJung jung = new GraphJung(tinkerBlueprints);
        PageRank pr = new PageRank<Vertex, Edge>(jung, 0.15d);
        pr.evaluate();
        long t_endPagerank = Instant.now().toEpochMilli();

        /* Write running report to file */
        stats.add(limit + "");
        stats.add(graph.getNodeCount() + "");
        stats.add(graph.getEdgeCount() + "");
        stats.add(((2.0 * graph.getNodeCount()) / (graph.getEdgeCount() * (graph.getEdgeCount() - 1.0))) + "");
        stats.add((t_endQuery-t_startQuery) + "");
        stats.add((t_endBuild-t_startBuild) + "");
        stats.add((t_endPagerank-t_startPagerank) + "");
        stats.add((t_endLayout-t_startLayout) + "");
        stats.add("files/drugbank/seq/results/" + ID + "/graph-layout.graphml");

        CsvWriter writerBody = new CsvWriter(new FileWriter(stats_file, true), new CsvWriterSettings());
        writerBody.writeRow(stats);
        writerBody.close();

        System.exit(1);
    }

    /** Fast file reading function */
    private static String readFile(String path, Charset encoding) throws IOException {
        FileInputStream inputStream =new FileInputStream(path);
        String contents = IOUtils.toString(inputStream, encoding.toString());
        IOUtils.closeQuietly(inputStream);
        return contents;
    }
}

