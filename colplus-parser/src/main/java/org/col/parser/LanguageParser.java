package org.col.parser;

import java.io.IOException;
import java.util.Map;

import com.google.common.collect.Maps;
import org.gbif.utils.file.csv.CSVReader;
import org.gbif.utils.file.csv.CSVReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for languages that turns native or english names and iso 639 2 or 3 letter codes into 3 letter codes.
 */
public class LanguageParser extends ParserBase<String> {
  private static final Logger LOG = LoggerFactory.getLogger(LanguageParser.class);
  public static String ISO_CODE_FILE = "iso-639-3_Name_Index_20190408.tab";
  
  public static final Parser<String> PARSER = new LanguageParser();
  
  private final Map<String, String> mapping = Maps.newHashMap();
  
  public LanguageParser() {
    super(String.class);
    addMappings();
  }
  
  private void addMapping(String resFile, int isoCol, int... valCols) {
    try {
      LOG.info("Reading language mapping {}", resFile);
      CSVReader reader = dictReader(resFile);
      while (reader.hasNext()) {
        String[] row = reader.next();
        if (row.length == 0) continue;
        if (row.length < isoCol) {
          LOG.info("Ignore invalid language mapping, {} line {} with only {} columns", resFile, reader.currLineNumber(), row.length);
          continue;
        }
  
        String iso = row[isoCol];
        if (iso != null) {
          for (int col : valCols) {
            add(row[col], iso);
          }
        }
      }
      reader.close();

    } catch (IOException e) {
      LOG.error("Failed to load {} mappings", resFile, e);
    }
  }
  
  private void addMappings(){
    // Id	Print_Name	Inverted_Name
    addMapping(ISO_CODE_FILE , 0,  0,1,2);
    // Id	Part2B	Part2T	Part1	Scope	Language_Type	Ref_Name	Comment
    addMapping("iso-639-3_20190408.tab", 0,  3,6);
    // Id	Ref_Name	Ret_Reason	Change_To	Ret_Remedy	Effective
    addMapping("iso-639-3_Retirements_20190408.tab", 3,  0,1);
    // ISO	english	native
    addMapping("language-native.tab", 0,  1,2);
    // custom manually curated entries: ISO3	VALUE
    addMapping("language-custom.tab", 0,  1);
  }
  
  /**
   * Adds more mappings to the main mapping dictionary, overwriting any potentially existing values.
   * Keys will be normalized with the same method used for parsing before inserting them to the mapping.
   * Blank strings and null values will be ignored!
   */
  public void add(String key, String iso3) {
    key = normalize(key);
    // keep the first mapping in case of clashes
    if (key != null && !mapping.containsKey(key)) {
      this.mapping.put(key, iso3.trim().toLowerCase());
    }
  }
  
  protected CSVReader dictReader(String resourceFilename) throws IOException {
    return CSVReaderFactory.build(getClass().getResourceAsStream("/parser/dicts/iso639/" + resourceFilename), "UTF8", "\t", null, 1);
  }
  
  
  @Override
  String parseKnownValues(String upperCaseValue) throws UnparsableException {
    return mapping.get(upperCaseValue);
  }
}
