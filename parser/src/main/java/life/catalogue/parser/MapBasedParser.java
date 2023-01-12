package life.catalogue.parser;

import life.catalogue.common.io.TabReader;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

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

  public MapBasedParser(Class valueClass, boolean throwUnparsableException) {
    super(valueClass, throwUnparsableException);
  }

  public void addMappings(String mappingResourceFile) {
    // read mappings from resource file?
    try (TabReader reader = dictReader(mappingResourceFile)){
      LOG.info("Reading mappings from {}", mappingResourceFile);
      for (String[] row : reader) {
        if (row.length == 0) continue;
        if (row.length == 1) {
          LOG.debug("Ignore unmapped value {} on line {}", row[0], reader.getContext().currentLine());
          continue;
        }
        if (row.length == 2 && Strings.isNullOrEmpty(row[1])) {
          continue;
        }
        if (row.length > 2) {
          LOG.info("Ignore invalid mapping in {}, line {} with {} columns", mappingResourceFile, reader.getContext().currentLine(), row.length);
          continue;
        }
        T val = mapNormalisedValue(row[1]);
        if (val != null) {
          add(row[0], val);
        } else {
          LOG.warn("Value {} cannot be mapped to {}. Ignore mapping to {}", row[1], valueClass.getSimpleName(), row[0]);
        }
      }
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
   *
   * @return any previously existing value if it was different from the new one, otherwise null
   */
  public T add(String key, T value) {
    key = normalize(key);
    if (key != null) {
      T prev = this.mapping.put(key, value);
      if (prev != value) return prev;
    }
    return null;
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
