package life.catalogue.parser;

import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.GeoTime;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.TabReader;
import life.catalogue.common.text.CSVUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

/**
 * Parser for geochronological time spans.
 *
 * TODO: Include translations and synonyms from the following sources:
 *
 * WikiData: https://www.wikidata.org/wiki/Q43521
 * ICS translated: https://vocabs.ardc.edu.au/viewById/196
 * INSPIRE: http://inspire.ec.europa.eu/codelist/GeochronologicEraValue
 * German stratigraphy comission: https://www.geokartieranleitung.de/Fachliche-Grundlagen/Stratigraphie-Kartiereinheiten/Stratigraphie-der-Bundesrepublik/Chronostratigraphische-Einheiten
 * PalaeoBiologyDB (missing units): https://paleobiodb.org/data1.2/intervals/list.json?scale=all
 *
 * If the same time period occurs in several sources above the first determines the properties, e.g. year bounderies
 */
public class GeoTimeParser extends ParserBase<GeoTime> {
  private static final Logger LOG = LoggerFactory.getLogger(GeoTimeParser.class);
  private static final Pattern SLASH = Pattern.compile("^(.+)/(.+)$");
  private static final Pattern STEMMING = Pattern.compile("(I?AN|EN|ANO|IUM|AIDD|AAN)$");
  public static final GeoTimeParser PARSER = new GeoTimeParser();
  
  // normalised key -> time name
  private final Map<String, String> mapping = Maps.newHashMap();
  
  public GeoTimeParser() {
    super(GeoTime.class);
    addMappings();
    // manual mapping file
    try (TabReader reader = dictReader("geotime.csv")){
      for (String[] row : reader) {
        if (row.length != 2) continue;
        GeoTime gt = GeoTime.byName(row[1]);
        add(row[0], gt, true);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Bad parser file");
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

    // add alternatives from CSV file
    CSVUtils.parse(Resources.stream(GeoTime.ISC_RESOURCE), 1).forEach(row -> {
      var gt = GeoTime.byName(row.get(0));
      String labels = row.get(5);
      if (labels != null) {
        for (String label : labels.split("\\|")) {
         add(label, gt, true);
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
        LOG.debug("GeoTime {} has the same name as existing {}", time, dup);
      }
    }
  }

  @Override
  public Optional<? extends GeoTime> parse(String value) throws UnparsableException {
    // look for million years as an alternative to names geo times
    if (value != null) {
      Pattern MA = Pattern.compile("^([0-9]+(?:[,.][0-9]+)?)\\s*Ma");
      var m = MA.matcher(value);
      if (m.find()) {
        double ma = Double.parseDouble(m.group(1).replaceFirst(",", "."));
        return findByMa(ma);
      }
    }
    return super.parse(value);
  }

  private Optional<GeoTime> findByMa(double ma) {
    double minDuration = Double.MAX_VALUE;
    GeoTime best = null;
    for (var gt : GeoTime.TIMES.values()) {
      System.out.println(String.format("%s: %s - %s", gt.toString(), gt.getStart(), gt.getEnd()));
      if (gt.includes(ma) && (
        best == null || gt.duration() != null && minDuration > gt.duration()
      )) {
        best = gt;
        minDuration = ObjectUtils.coalesce(gt.duration(), minDuration);
      }
    }
    return Optional.ofNullable(best);
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
