package org.col.parser;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Maps;
import org.col.api.vocab.GeoTime;
import org.gbif.utils.file.csv.CSVReader;
import org.gbif.utils.file.csv.CSVReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    try (CSVReader reader = dictReader(resFile)) {
      while (reader.hasNext()) {
        String[] row = reader.next();
        if (row.length == 0) continue;
        if (row.length < nameCol) {
          LOG.info("Ignore invalid geotime mapping, {} line {} with only {} columns", resFile, reader.currLineNumber(), row.length);
          continue;
        }
  
        String name = row[nameCol];
        if (name != null) {
          GeoTime time = GeoTime.byName(name);
          if (time == null) {
            LOG.info("Ignore invalid geotime mapping for non existing geotime {} on line {}", name, reader.currLineNumber());
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
      add(t.getName() + " " + t.getUnit(), t, false);
      // TODO: translate lower/early upper/late
    });

    // Id	Part2B	Part2T	Part1	Scope	Language_Type	Ref_Name	Comment
    //addMapping("iso-639-3_20190408.tab", 0,  3,6);
  }
  
  /**
   * Adds more mappings to the main mapping dictionary, overwriting any potentially existing values.
   * Keys will be normalized with the same method used for parsing before inserting them to the mapping.
   * Blank strings and null values will be ignored!
   */
  public void add(String key, GeoTime time, boolean reportDuplicate) {
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
    // stem locale specific suffix
    // https://en.wikipedia.org/wiki/List_of_geochronologic_names#cite_note-1
    Matcher m = STEMMING.matcher(x);
    if (m.find()) {
      return m.replaceFirst("");
    }
    return x;
  }
  
  protected CSVReader dictReader(String resourceFilename) throws IOException {
    return CSVReaderFactory.build(getClass().getResourceAsStream("/parser/dicts/iso639/" + resourceFilename), "UTF8", "\t", null, 1);
  }
  
  
  @Override
  GeoTime parseKnownValues(String upperCaseValue) throws UnparsableException {
    return GeoTime.byName(mapping.get(upperCaseValue));
  }
}
