package life.catalogue.parser;

import com.google.common.collect.Maps;
import life.catalogue.api.vocab.Language;
import life.catalogue.common.io.Resources;
import org.apache.commons.lang3.StringUtils;
import org.gbif.utils.file.csv.CSVReader;
import org.gbif.utils.file.csv.CSVReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Parser for languages that turns native or english names and iso 639 2 or 3 letter codes into 3 letter codes.
 */
public class LanguageParser extends ParserBase<Language> {
  private static final Logger LOG = LoggerFactory.getLogger(LanguageParser.class);
  
  public static final LanguageParser PARSER = new LanguageParser();
  
  private final Map<String, String> mapping = Maps.newHashMap();
  
  public LanguageParser() {
    super(String.class);
    addMappings();
  }
  
  private void addMapping(String resourceFile, int isoCol, Integer forceCol, int... valCols) {
    LOG.info("Reading language mapping {}", resourceFile);
    try {
      addMapping(getClass().getResourceAsStream("/parser/dicts/iso639/" + resourceFile), isoCol, forceCol, valCols);
    } catch (IOException e) {
      LOG.error("Failed to load {} mappings", resourceFile, e);
    }
  }
  
  private void addMapping(InputStream stream, int isoCol, Integer forceCol, int... valCols) throws IOException {
    try (CSVReader reader = CSVReaderFactory.build(stream, "UTF8", "\t", null, 1)) {
      while (reader.hasNext()) {
        String[] row = reader.next();
        if (row.length == 0) continue;
        if (row.length < isoCol) {
          LOG.info("Ignore invalid language mapping, line {} with only {} columns", reader.currLineNumber(), row.length);
          continue;
        }
  
        String iso = row[isoCol];
        if (iso != null) {
          if (forceCol != null) {
            add(row[forceCol], iso, true);
          }
          for (int col : valCols) {
            add(row[col], iso, false);
          }
        }
      }
    }
  }
  
  private void addMappings(){
    // Id	Print_Name	Inverted_Name
    Language.LANGUAGES.values().forEach(l -> {
      add(l.getCode(), l.getCode(), true);
      add(l.getTitle(), l.getCode(), true);
    });
    try {
      // we add the authority file again to also read duplicate titles and inverse titles
      InputStream isoStream = Resources.stream(Language.ISO_CODE_FILE);
      // Id	Print_Name	Inverted_Name
      addMapping(isoStream , 0,  null, 0,1,2);
    } catch (IOException e) {
      LOG.error("Failed to load {} mappings", Language.ISO_CODE_FILE, e);
    }
    // Id	Part2B	Part2T	Part1	Scope	Language_Type	Ref_Name	Comment
    addMapping("iso-639-3_20190408.tab", 0,  3,6);
    // Id	Ref_Name	Ret_Reason	Change_To	Ret_Remedy	Effective
    addMapping("iso-639-3_Retirements_20190408.tab", 3,  null, 0,1);
    // ISO	english	native
    addMapping("language-native.tab", 0,  null, 1,2);
    // custom manually curated entries: ISO3	VALUE
    addMapping("language-custom.tab", 0,  1);
    // WikiData SPARQL download: url	iso	ietf	native	len	lde	lfr	les	lru	lzh	lpt	lit
    addMapping("query.tsv", 1,  null, 2,3,4,5,6,7,8,9,10,11);
  }
  
  /**
   * Adds more mappings to the main mapping dictionary, overwriting any potentially existing values.
   * Keys will be normalized with the same method used for parsing before inserting them to the mapping.
   * Blank strings and null values will be ignored!
   */
  public void add(String key, String iso3, boolean force) {
    key = normalize(key);
    // unless forced keep the first mapping in case of clashes
    if (key != null && !StringUtils.isBlank(iso3) && (force || !mapping.containsKey(key))) {
      Language l = Language.byCode(iso3.trim().toLowerCase());
      if (l == null) {
        LOG.debug("Language code {} mapped from >>{}<< does not exist", iso3, key);
      } else {
        this.mapping.put(key, l.getCode());
      }
    }
  }
  
  @Override
  Language parseKnownValues(String upperCaseValue) throws UnparsableException {
    return Language.byCode(mapping.getOrDefault(upperCaseValue, null));
  }
}
