package life.catalogue.parser;

import life.catalogue.api.util.JsonLdReader;
import life.catalogue.api.vocab.GeoTime;
import life.catalogue.api.vocab.GeoTimeFactory;
import life.catalogue.common.io.TabReader;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

/**
 * Parser for geochronological time spans.
 *
 * Official times included from the following sources:
 *
 * GeoSciML version of ISC 2017: http://resource.geosciml.org/vocabulary/timescale/isc2017.jsonld
 * INSPIRE: http://inspire.ec.europa.eu/codelist/GeochronologicEraValue
 * German stratigraphy comission: https://www.geokartieranleitung.de/Fachliche-Grundlagen/Stratigraphie-Kartiereinheiten/Stratigraphie-der-Bundesrepublik/Chronostratigraphische-Einheiten
 * PalaeoBiologyDB (missing units): https://paleobiodb.org/data1.2/intervals/list.json?scale=1..4
 *
 * If the same time period occurs in several sources above the first determines the properties, e.g. year bounderies
 */
public class GeoTimeParser extends ParserBase<GeoTime> {
  private static final Logger LOG = LoggerFactory.getLogger(GeoTimeParser.class);
  private static final Pattern SLASH = Pattern.compile("^(.+)/(.+)$");
  public static String INSPIRE_FILE = "INSPIRE-GeochronologicEraValue.en.csv";
  public static String GERMAN_FILE = "GERMAN-export_20190913_120040.csv";
  public static String PBDB_FILE = "pbdb_data.csv";
  
  private static final Pattern STEMMING = Pattern.compile("(I?AN|EN|ANO|IUM|AIDD|AAN)$");
  public static final GeoTimeParser PARSER = new GeoTimeParser();
  
  // normalised key -> time name
  private final Map<String, String> mapping = Maps.newHashMap();
  
  public GeoTimeParser() {
    super(GeoTime.class);
    addMappings();
  }
  
  private void addMapping(String resFile, int nameCol, int... valCols) {
    LOG.info("Reading custom geotime mapping {}", resFile);
    try (TabReader reader = dictReader(resFile)){
      for (String[] row : reader) {
        if (row.length == 0) continue;
        if (row.length < nameCol) {
          LOG.info("Ignore invalid geotime mapping, {} line {} with only {} columns", resFile, reader.getContext().currentLine(), row.length);
          continue;
        }
  
        String name = row[nameCol];
        if (name != null) {
          GeoTime time = GeoTime.byName(name);
          if (time == null) {
            LOG.info("Ignore invalid geotime mapping for non existing geotime {} on line {}", name, reader.getContext().currentLine());
          } else {
            for (int col : valCols) {
              add(row[col], time, false);
            }
          }
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to load {} mappings", resFile, e);
    }
  }
  
  private void addMappings(){
    // map official names
    GeoTime.TIMES.values().forEach(t -> add(t.getName(), t, true));
    GeoTime.TIMES.values().forEach(t -> {
      // map official names
      add(t.getName(), t, false);
      // map official name incl unit
      add(t.getName() + " " + t.getType(), t, false);
      // TODO: translate lower/early upper/late
    });

    // add alternatives from main file
    GeoTimeFactory.readJsonLD().forEach(item -> {
      final String acceptedName;
      if (item.isReplacedBy == null) {
        acceptedName = GeoTimeFactory.removePrefix(item.id);
      } else {
        acceptedName = GeoTimeFactory.removePrefix(item.isReplacedBy);
      }
      final GeoTime accepted = GeoTime.byName(acceptedName);
      if (accepted == null) {
        LOG.warn("Unknown accepted geotime {}", acceptedName);
      }
  
      if (item.prefLabel != null) {
        for (JsonLdReader.Label label : item.prefLabel) {
          add(label.value, accepted, false);
        }
      }
      if (item.altLabel != null) {
        for (JsonLdReader.Label label : item.altLabel) {
          add(label.value, accepted, false);
        }
      }
    });
  }
  
  /**
   * Adds more mappings to the main mapping dictionary, overwriting any potentially existing values.
   * Keys will be normalized with the same method used for parsing before inserting them to the mapping.
   * Blank strings and null values will be ignored!
   */
  public void add(String key, GeoTime time, boolean reportDuplicate) {
    // if we have a slash we actually have further alternatives to add
    Matcher m = SLASH.matcher(key);
    if (m.find()) {
      // if second part contains no space take it as it is. Otherwise prepend the first bits
      if (m.group(2).contains(" ")) {
        String[] parts = m.group(2).split(" ", 2);
        add(m.group(1)+" "+parts[1], time, reportDuplicate);
        add(m.group(2), time, reportDuplicate);
      } else {
        add(m.group(1), time, reportDuplicate);
        add(m.group(2), time, reportDuplicate);
      }
    }
    key = normalize(key);
    // keep the first mapping in case of clashes
    if (key != null){
      if (!mapping.containsKey(key)) {
        this.mapping.put(key, time.getName());
      
      } else if (reportDuplicate) {
        GeoTime dup = GeoTime.byName(mapping.get(key));
        LOG.info("GeoTime {} has the same name as existing {}", time, dup);
      }
    }
  }
  
  @Override
  String normalize(String x) {
    x = super.normalize(x);
    if (x != null) {
      x = x.replaceAll(" ", "");
      // stem locale specific suffix
      // https://en.wikipedia.org/wiki/List_of_geochronologic_names#cite_note-1
      Matcher m = STEMMING.matcher(x);
      if (m.find()) {
        return m.replaceFirst("");
      }
    }
    return x;
  }
  
  @Override
  GeoTime parseKnownValues(String upperCaseValue) throws UnparsableException {
    return GeoTime.byName(mapping.get(upperCaseValue));
  }
}
