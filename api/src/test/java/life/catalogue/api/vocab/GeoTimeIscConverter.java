package life.catalogue.api.vocab;

import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.text.CSVUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;

import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

/**
 * Converts the RDF Turtle file of the International Chronostratigraphic Chart into a simple CSV file,
 * which is placed into /vocab/geotime/geotime.csv resources so it is picked up during builds.
 * The conversion should be triggered manually whenever the turtle file is updated from
 * https://github.com/i-c-stratigraphy/chart
 * or the patches or legacy labels change. See readme.md next to the files.
 */
public class GeoTimeIscConverter {

  private static final String CHART_RESOURCE = "vocab/geotime/chart-2026-06.ttl";
  private static final String PATCH_RESOURCE = "vocab/geotime/patch.csv";
  private static final String LEGACY_LABEL_RESOURCE = "vocab/geotime/legacy-labels.csv";

  private static final String GTSD = "https://data.stratigraphy.org/data/gts/";
  private static final String GTS = "http://resource.geosciml.org/ontology/timescale/gts#";
  private static final String TIME = "http://www.w3.org/2006/time#";
  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
  private static final String SCHEMA = "https://schema.org/";

  static class GTItem {
    final String id;
    GeoTimeType type;
    Double start;
    Double end;
    String broader;
    String color;
    final Set<String> labels = new TreeSet<>();

    GTItem(String id) {
      this.id = id;
    }
  }

  private static String id(Resource r) {
    if (!r.getURI().startsWith(GTSD)) {
      throw new IllegalStateException("Unexpected namespace in " + r);
    }
    return r.getURI().substring(GTSD.length());
  }

  private static Double bound(Model model, Resource r, String boundary) {
    var b = r.getPropertyResourceValue(model.createProperty(TIME, boundary));
    return b.getRequiredProperty(model.createProperty(GTSD, "inMYA")).getDouble();
  }

  private static List<GTItem> read(Model model) {
    Property rank = model.createProperty(GTS, "rank");
    Property prefLabel = model.createProperty(SKOS, "prefLabel");
    Property broader = model.createProperty(SKOS, "broader");
    Property color = model.createProperty(SCHEMA, "color");

    List<GTItem> items = new ArrayList<>();
    for (Resource r : model.listSubjectsWithProperty(rank).toList()) {
      GTItem item = new GTItem(id(r));
      // Pridoli has 2 ranks as an epoch that is not divided into ages. The higher one tells what it is
      item.type = r.listProperties(rank).toList().stream()
                   .map(s -> GeoTimeType.valueOf(s.getResource().getLocalName().toUpperCase().replaceAll("-", "")))
                   .min(Comparator.naturalOrder())
                   .orElseThrow();
      item.start = bound(model, r, "hasBeginning");
      item.end = bound(model, r, "hasEnd");
      item.color = r.getRequiredProperty(color).getString();
      var parent = r.getPropertyResourceValue(broader);
      item.broader = parent == null ? null : id(parent);
      r.listProperties(prefLabel).forEach(s -> item.labels.add(s.getString()));
      items.add(item);
    }
    return items;
  }

  private static GTItem find(Map<String, GTItem> items, String name, String resource) {
    var item = items.get(name);
    if (item == null) {
      throw new IllegalStateException("Unknown geotime " + name + " in " + resource);
    }
    return item;
  }

  private static void addLegacyLabels(Map<String, GTItem> items) {
    CSVUtils.parse(Resources.stream(LEGACY_LABEL_RESOURCE), 1).forEach(row -> {
      find(items, row.get(0), LEGACY_LABEL_RESOURCE).labels.addAll(List.of(row.get(1).split("\\|")));
    });
  }

  private static Double patched(Double current, String value, GTItem item) {
    Double patch = Double.valueOf(value);
    if (patch.equals(current)) {
      throw new IllegalStateException(item.id + " is correct in the chart, remove the patch");
    }
    return patch;
  }

  private static void applyPatches(Map<String, GTItem> items) {
    CSVUtils.parse(Resources.stream(PATCH_RESOURCE), 1).forEach(row -> {
      var item = find(items, row.get(0), PATCH_RESOURCE);
      String value = row.get(2);
      switch (row.get(1)) {
        case "start" -> item.start = patched(item.start, value, item);
        case "end" -> item.end = patched(item.end, value, item);
        case "removeLabel" -> {
          if (!item.labels.remove(value)) {
            throw new IllegalStateException(item.id + " has no label " + value + ", remove the patch");
          }
        }
        default -> throw new IllegalStateException("Unknown patch field " + row.get(1));
      }
    });
  }

  public void convert(File fo) throws IOException {
    Model model;
    try (InputStream in = Resources.stream(CHART_RESOURCE)) {
      model = RDFParser.create()
                 .source(in)
                 .lang(RDFLanguages.TTL)
                 .errorHandler(ErrorHandlerFactory.errorHandlerStrict)
                 .toDataset()
                 .getDefaultModel();
    }
    final Map<String, GTItem> items = read(model).stream().collect(Collectors.toMap(i -> i.id, i -> i));
    addLegacyLabels(items);
    applyPatches(items);

    var gtMap = new HashMap<String, GeoTime>();
    // sorts by unit, then time so we can find parents in one go
    var times = items.values().stream()
                     .map(i -> new GeoTime(i.id, i.type, i.start, i.end, null, i.color))
                     .sorted()
                     .collect(Collectors.toList());
    for (GeoTime src : times) {
      var item = items.get(src.getName());
      if (item.broader != null && !gtMap.containsKey(item.broader)) {
        throw new IllegalStateException("parent " + item.broader + " missing");
      }
      gtMap.put(src.getName(), new GeoTime(src, gtMap.get(item.broader)));
    }

    CsvWriterSettings SETTINGS = new CsvWriterSettings();
    try (var writer = UTF8IoUtils.writerFromFile(fo)) {
      CsvWriter csv = new CsvWriter(writer, SETTINGS);
      writer.write("name,type,start,end,parent,labels,color\n");
      for (var gt : gtMap.values().stream().sorted().collect(Collectors.toList())) {
        var item = items.get(gt.getName());
        csv.writeRow(new String[]{
          gt.getName(), gt.getType().name(), gt.getStart().toString(), gt.getEnd().toString(),
          gt.getParent()==null?"":gt.getParent().getName(), String.join("|", item.labels), gt.getColor()
        });
      }
    }
  }

  public static void main(String[] args) throws Exception {
    var conv = new GeoTimeIscConverter();
    conv.convert(new File("/Users/markus/code/col/backend/api/src/main/resources/vocab/geotime/geotime.csv"));
  }
}
