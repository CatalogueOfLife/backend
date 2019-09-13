package org.col.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.col.api.vocab.GeoTime;
import org.gbif.utils.file.csv.CSVReader;
import org.gbif.utils.file.csv.CSVReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for geochronological time spans.
 */
public class GeoTimeParser extends ParserBase<GeoTime> {
  private static final Logger LOG = LoggerFactory.getLogger(GeoTimeParser.class);
  public static String ISO_CODE_FILE = "iso-639-3_Name_Index_20190408.tab";
  
  private static final Pattern STEMMING = Pattern.compile("(I?AN|EN|ANO|IUM|AIDD|AAN)$");
  public static final GeoTimeParser PARSER = new GeoTimeParser();
  
  // normalied key -> time name
  private final Map<String, String> mapping = Maps.newHashMap();
  // name -> time
  private final ImmutableMap<String, GeoTime> times;
  
  public GeoTimeParser() {
    super(GeoTime.class);
    times = ImmutableMap.copyOf(
        readTimes().stream().collect(Collectors.toMap(GeoTime::getName, Function.identity()))
    );
    //addMappings();
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
          GeoTime time = times.getOrDefault(name, null);
          if (time == null) {
            LOG.info("Ignore invalid geotime mapping for non existing geotime {} on line {}", name, reader.currLineNumber());
          } else {
            for (int col : valCols) {
              add(row[col], time);
            }
          }
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to load {} mappings", resFile, e);
    }
  }
  
  private void addSubdivision(GeoTime time, GeoTime subdivision) {
    Preconditions.checkArgument(subdivision.getUnit().ordinal() > time.getUnit().ordinal());
    time.getSubdivisions().add(subdivision);
  }
  
  private void addMappings(){
    // map official names
    times.values().forEach(t -> add(t.getName(), t));

    // Id	Part2B	Part2T	Part1	Scope	Language_Type	Ref_Name	Comment
    addMapping("iso-639-3_20190408.tab", 0,  3,6);
  }
  
  private List<GeoTime> readTimes(){
    // Id	Print_Name	Inverted_Name
    final int isoCol = 0;
    final int titleCol = 1;
    List<GeoTime> times = new ArrayList<>();
    try (CSVReader reader = dictReader(ISO_CODE_FILE)){
      LOG.info("Reading language code titles from {}", ISO_CODE_FILE);
      while (reader.hasNext()) {
        String[] row = reader.next();
        if (row.length == 0) continue;
        String iso = row[isoCol];
        if (iso != null) {
          //TODO: times
        }
      }
    
    } catch (IOException e) {
      LOG.error("Failed to load language titles from {}", ISO_CODE_FILE, e);
    }
    return times;
  }
  
  /**
   * In natural order
   */
  public List<GeoTime> times() {
    return new ArrayList<>(times.values());
  }
  
  /**
   * In alphabetical order
   */
  public GeoTime time(String name) {
    return times.getOrDefault(name, null);
  }
  
  /**
   * Adds more mappings to the main mapping dictionary, overwriting any potentially existing values.
   * Keys will be normalized with the same method used for parsing before inserting them to the mapping.
   * Blank strings and null values will be ignored!
   */
  public void add(String key, GeoTime time) {
    key = normalize(key);
    // keep the first mapping in case of clashes
    if (key != null && !mapping.containsKey(key)) {
      this.mapping.put(key, time.getName());
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
    return time(mapping.get(upperCaseValue));
  }
}
