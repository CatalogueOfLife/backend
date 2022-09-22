package life.catalogue.api.vocab;

import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

/**
 * Converts the ISC RDF Turtle source file into a simple CSV file for the GeoTimeFactory resources.
 * The conversion should be triggered manually whenever the turtle file is updated from
 * https://github.com/CGI-IUGS/timescale-data/tree/master/rdf
 *
 * and placed into /vocab/geotime/geotime.csv resources so it is picked up durign builds.
 */
public class GeoTimeIscConverter {
  
  private static final Logger LOG = LoggerFactory.getLogger(GeoTimeIscConverter.class);
  private static String ISC_RESOURCE = "vocab/geotime/isc2020.ttl";
  // "younger bound -315.2 +|-0.2 Ma"
  // "younger bound -0.0 Ma"
  // "older bound -0.0117 Ma"

  private static Pattern BOUNDS = Pattern.compile("(young|old)(?:er)? +bound +-?([0-9.]+) [^a-z]*Ma", Pattern.CASE_INSENSITIVE);

  static class GTItem {
    public final Resource resource;
    public final String id;
    public String rank;
    public List<String> labels = new ArrayList<>();
    public String broader;
    public List<String> comment = new ArrayList<>();

    public GTItem(Resource resource) {
      this.resource = resource;
      this.id = resource.getLocalName();
    }

    GeoTime toGeotime(Function<String, GeoTime> parentFunc) {
      GeoTime parent = null;
      if (parentFunc != null && broader != null) {
        // lookup in provided map
        parent = parentFunc.apply(broader);
        if (parent == null) {
          throw new IllegalStateException("parent "+broader+" missing");
        }
      }
      Double[] bounds = new Double[2];
      if (comment != null) {
        for (String c : comment) {
          Matcher m = BOUNDS.matcher(c);
          if (m.find()) {
            int idx = m.group(1).startsWith("y") ? 1 : 0;
            Double ma = Double.parseDouble(m.group(2));
            bounds[idx] = ma;
          }
        }
      }
      var type = GeoTimeType.valueOf(rank.toUpperCase().replaceAll("-", ""));
      return new GeoTime(id, type, bounds[0], bounds[1], parent);
    }
  }

  public void convert(File fo) throws IOException {
    Dataset dataset;
    try (InputStream in = Resources.stream(ISC_RESOURCE)) {
      dataset = RDFParser.create()
                 .source(in)
                 .lang(RDFLanguages.TTL)
                 .errorHandler(ErrorHandlerFactory.errorHandlerStrict)
                 .base("http://resource.geosciml.org/vocabulary/timescale/gts2020")
                 .toDataset();
    }
    String PREFIXES ="PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                   + "PREFIX gts: <http://resource.geosciml.org/ontology/timescale/gts#> "
                   + "PREFIX isc: <http://resource.geosciml.org/classifier/ics/ischart/> "
                   + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                   + "PREFIX skos: <http://www.w3.org/2004/02/skos/core#> "
                   + "PREFIX dct: <http://purl.org/dc/terms/> ";

    String query = PREFIXES + "SELECT ?s {?s rdf:type gts:GeochronologicEra}";
    List<RDFNode> nodes = new ArrayList<>();
    try(QueryExecution qExec = QueryExecution.dataset(dataset).query(query).build() ) {
      ResultSet rs = qExec.execSelect() ;
      while (rs.hasNext()) {
        var qs = rs.next();
        nodes.add(qs.get("s"));
      }
    }

    final List<GTItem> items = new ArrayList<>();
    for (var n : nodes) {
      System.out.println(n.asResource().getLocalName());
      GTItem item = new GTItem(n.asResource());
      items.add(item);

      query = PREFIXES + "SELECT ?o { isc:" + item.id + " rdfs:comment ?o}";
      try(QueryExecution qExec = QueryExecution.dataset(dataset).query(query).build() ) {
        ResultSet rs = qExec.execSelect() ;
        while (rs.hasNext()) {
          var qs = rs.next();
          String comment = qs.getLiteral("o").getString();
          item.comment.add(comment);
        }
      }

      query = PREFIXES + "SELECT ?o { isc:" + item.id + " gts:rank ?o}";
      try(QueryExecution qExec = QueryExecution.dataset(dataset).query(query).build() ) {
        ResultSet rs = qExec.execSelect() ;
        while (rs.hasNext()) {
          var qs = rs.next();
          Resource rank = qs.getResource("o");
          item.rank = rank.getLocalName();
        }
      }

      query = PREFIXES + "SELECT ?o { isc:" + item.id + " skos:prefLabel ?o}";
      try(QueryExecution qExec = QueryExecution.dataset(dataset).query(query).build() ) {
        ResultSet rs = qExec.execSelect() ;
        while (rs.hasNext()) {
          var qs = rs.next();
          String label = qs.getLiteral("o").getString();
          item.labels.add(label);
        }
      }

      query = PREFIXES + "SELECT ?o { isc:" + item.id + " skos:altLabel ?o}";
      try(QueryExecution qExec = QueryExecution.dataset(dataset).query(query).build() ) {
        ResultSet rs = qExec.execSelect() ;
        while (rs.hasNext()) {
          var qs = rs.next();
          String label = qs.getLiteral("o").getString();
          item.labels.add(label);
        }
      }

      query = PREFIXES + "SELECT ?s { ?s dct:isReplacedBy isc:" + item.id + "}";
      try(QueryExecution qExec = QueryExecution.dataset(dataset).query(query).build() ) {
        ResultSet rs = qExec.execSelect() ;
        while (rs.hasNext()) {
          var qs = rs.next();
          String label = qs.getResource("s").getLocalName();
          item.labels.add(label);
        }
      }

      query = PREFIXES + "SELECT ?o { isc:" + item.id + " skos:broader ?o}";
      try(QueryExecution qExec = QueryExecution.dataset(dataset).query(query).build() ) {
        ResultSet rs = qExec.execSelect() ;
        while (rs.hasNext()) {
          var qs = rs.next();
          item.broader = qs.getResource("o").getLocalName();
        }
      }
    }

    // way too much maps, but I cant think of a more elegant way right now
    var itMap = items.stream().collect(Collectors.toMap(g -> g.id, Function.identity()));
    var gtMap = new HashMap<String, GeoTime>();
    // sorts by unit, then time so we can find parents in one go
    var times = items.stream().map(i -> i.toGeotime(null)).sorted().collect(Collectors.toList());
    for (GeoTime src : times) {
      var item = itMap.get(src.getName());
      GeoTime gt = new GeoTime(src, gtMap.get(item.broader));
      gtMap.put(gt.getName(), gt);
    }

    CsvWriterSettings SETTINGS = new CsvWriterSettings();
    try (var writer = UTF8IoUtils.writerFromFile(fo)) {
      CsvWriter csv = new CsvWriter(writer, SETTINGS);
      writer.write("name,type,start,end,parent,labels\n");
      for (var gt : gtMap.values().stream().sorted().collect(Collectors.toList())) {
        var item = itMap.get(gt.getName());
        csv.writeRow(new String[]{
          gt.getName(), str(gt.getType()), str(gt.getStart()), str(gt.getEnd()), gt.getParent()==null?"":gt.getParent().getName(), str(item.labels)
        });
      }
    }
  }

  private static String str(Enum<?> x) {
    return x == null ? "" : x.name();
  }
  private static String str(Double x) {
    return x == null ? "" : x.toString();
  }
  private static String str(List<String> x) {
    return x == null || x.isEmpty() ? "" : String.join("|", x);
  }
  
  public static void main(String[] args) throws Exception {
    var conv = new GeoTimeIscConverter();
    conv.convert(new File("/Users/markus/code/col/backend/api/src/main/resources/vocab/geotime/geotime.csv"));
  }
}
