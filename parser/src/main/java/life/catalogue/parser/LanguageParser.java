package life.catalogue.parser;

import life.catalogue.api.vocab.Language;
import life.catalogue.common.io.TabReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

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
    try (TabReader reader = TabReader.tab(stream, StandardCharsets.UTF_8, 1, 2)) {
      for (String[] row : reader) {
        if (row.length == 0) continue;
        if (row.length < isoCol) {
          LOG.info("Ignore invalid language mapping, line {} with only {} columns", reader.getContext().currentLine(), row.length);
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
    } catch (IOException e) {

    }
  }
  
  private void addMappings(){
    Language.LANGUAGES.values().forEach(l -> {
      add(l.getCode(), l.getCode(), true);
      add(l.getTitle(), l.getCode(), true);
    });
    try {
      // we add the authority file again to also read 2 letter codes
      // Id	Part2B	Part2T	Part1	Scope	Language_Type	Ref_Name	Comment
      addMapping(Language.class.getResourceAsStream("/"+Language.ISO_CODE_FILE), 0, 3, 1,2,6);
    } catch (IOException e) {
      LOG.error("Failed to load {} mappings", Language.ISO_CODE_FILE, e);
    }
    // Id	Print_Name	Inverted_Name
    addMapping("iso-639-3_Name_Index"+Language.ISO_VERSION, 0,  null,1,2);
    // Id	Ref_Name	Ret_Reason	Change_To	Ret_Remedy	Effective
    addMapping("iso-639-3_Retirements"+Language.ISO_VERSION, 3,  null, 0,1);
    // ISO	english	native
    addMapping("language-native.tab", 0,  null, 1,2);
    // custom manually curated entries: ISO3	VALUE
    addMapping("language-custom.tsv", 0,  1);
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
