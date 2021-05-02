package life.catalogue.parser;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.gbif.utils.file.csv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Parser with resource file based mappings.
 * Exactly two columns are expected.
 * The first column must contain the custom value, the 2nd value is the one being mapped to.
 */
public abstract class MapBasedParser<T> extends ParserBase<T> {
  private static final Logger LOG = LoggerFactory.getLogger(MapBasedParser.class);
  private final Map<String, T> mapping = Maps.newHashMap();
  
  public MapBasedParser(Class valueClass) {
    super(valueClass);
  }
  
  public void addMappings(String mappingResourceFile) {
    // read mappings from resource file?
    try {
      LOG.info("Reading mappings from {}", mappingResourceFile);
      CSVReader reader = dictReader(mappingResourceFile);
      while (reader.hasNext()) {
        String[] row = reader.next();
        if (row.length == 0) continue;
        if (row.length == 1) {
          LOG.debug("Ignore unmapped value {} on line {}", row[0], reader.currLineNumber());
          continue;
        }
        if (row.length == 2 && Strings.isNullOrEmpty(row[1])) {
          continue;
        }
        if (row.length > 2) {
          LOG.info("Ignore invalid mapping in {}, line {} with {} columns", mappingResourceFile, reader.currLineNumber(), row.length);
          continue;
        }
        T val = mapNormalisedValue(row[1]);
        if (val != null) {
          add(row[0], val);
        } else {
          LOG.warn("Value {} cannot be mapped to {}. Ignore mapping to {}", row[1], valueClass.getSimpleName(), row[0]);
        }
      }
      reader.close();
    } catch (IOException e) {
      LOG.error("Failed to load {} parser mappings from {}", valueClass.getSimpleName(), mappingResourceFile, e);
    }
  }
  
  /**
   * Maps the standard representation given in the resource file to the final type
   */
  protected abstract T mapNormalisedValue(String upperCaseValue);
  
  /**
   * Adds more mappings to the main mapping dictionary, overwriting any potentially existing values.
   * Keys will be normalized with the same method used for parsing before inserting them to the mapping.
   * Blank strings and null values will be ignored!
   */
  public void add(String key, T value) {
    key = normalize(key);
    if (key != null) {
      this.mapping.put(key, value);
    }
  }
  
  public void addNoOverwrite(String key, T value) {
    key = normalize(key);
    if (key != null) {
      if (mapping.containsKey(key)) {
        LOG.debug("{} ({}) exists already for value {}", key, value, mapping.get(key));
      } else {
        this.mapping.put(key, value);
      }
    }
  }

  public void add(Object key, T value) {
    if (key != null) {
      add(key.toString(), value);
    }
  }
  
  public void addNoOverwrite(Object key, T value) {
    if (key != null) {
      addNoOverwrite(key.toString(), value);
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
  T parseKnownValues(String upperCaseValue) {
    return mapping.get(upperCaseValue);
  }
}
