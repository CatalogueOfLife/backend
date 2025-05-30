package life.catalogue.parser;

import life.catalogue.common.io.TabReader;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Enums;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

/**
 *
 */
public abstract class EnumNoteParser<T extends Enum> extends ParserBase<EnumNote<T>> {
  private static final Logger LOG = LoggerFactory.getLogger(EnumNoteParser.class);
  private final Map<String, EnumNote<T>> mapping = Maps.newHashMap();
  private final Class<T> enumClass;
  
  public EnumNoteParser(String mappingResourceFile, Class<T> enumClass) {
    super(EnumNote.class);
    this.enumClass = enumClass;
    // read mappings from resource file
    LOG.info("Reading mappings from {}", mappingResourceFile);
    try (TabReader reader = dictReader(mappingResourceFile)){
      for (String[] row : reader) {
        if (row.length == 0) continue;
        if (row.length == 1) {
          LOG.debug("Ignore unmapped value {} on line {}", row[0], reader.getContext().currentLine());
          continue;
        }
        if (row.length > 3 || Strings.isNullOrEmpty(row[1])) {
          LOG.debug("Ignore invalid mapping in {}, line {} with {} columns", mappingResourceFile, reader.getContext().currentLine(), row.length);
          continue;
        }
        Optional<T> val = Enums.getIfPresent(enumClass, row[1]);
        String note = row.length == 3 ? row[2] : null;
        if (val.isPresent()) {
          add(row[0], val.get(), note);
        } else {
          LOG.info("Value {} not present in {} enumeration. Ignore mapping to {}", row[1], enumClass.getSimpleName(), row[0]);
        }
      };
    } catch (IOException e) {
      LOG.error("Failed to load {} parser mappings from {}", enumClass.getSimpleName(), mappingResourceFile, e);
    }
    // finally add native mappings, overriding anything found in files
    addNativeEnumMappings();
  }
  
  private void addNativeEnumMappings() {
    for (T e : enumClass.getEnumConstants()) {
      add(e.name(), e, null);
    }
  }
  
  /**
   * Adds more mappings to the main mapping dictionary, overwriting any potentially existing values.
   * Keys will be normalized with the same method used for parsing before inserting them to the mapping.
   * Blank strings and null values will be ignored!
   */
  public void add(String key, T value, String note) {
    key = normalize(key);
    if (key != null) {
      this.mapping.put(key, new EnumNote<>(value, note));
    }
  }
  
  @Override
  String normalize(String x) {
    x = super.normalize(x);
    if (x != null) {
      return x.replaceAll(" +", "");
    }
    return null;
  }
  
  @Override
  EnumNote<T> parseKnownValues(String upperCaseValue) {
    return mapping.get(upperCaseValue);
  }
}
