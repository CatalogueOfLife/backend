package org.col.admin.task.importer.neo;

import com.google.common.collect.Maps;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.neo4j.graphdb.Node;

import java.util.Map;

/**
 * Static utils for the NeoDb class
 */
public class NeoDbUtils {

  private NeoDbUtils() {
  }

  static void putIfNotNull(Map<String, Object> props, String property, String value) {
    if (value != null) {
      props.put(property, value);
    }
  }

  static void putIfNotNull(Map<String, Object> props, String property, Enum value) {
    if (value != null) {
      props.put(property, value.ordinal());
    }
  }

  /**
   * Sets a node property and removes it in case the property value is null.
   */
  static void setProperties(Node n, Map<String, Object> props) {
    if (props != null) {
      for (Map.Entry<String, Object> p : props.entrySet()) {
        setProperty(n, p.getKey(), p.getValue());
      }
    }
  }

  /**
   * Sets a node property and removes it in case the property value is null.
   */
  static void setProperty(Node n, String property, Object value) {
    if (value == null) {
      n.removeProperty(property);
    } else {
      n.setProperty(property, value);
    }
  }

  static Map<String, Object> neo4jProps(NeoTaxon tax) {
    Map<String, Object> props = Maps.newHashMap();
    putIfNotNull(props, NeoProperties.ID, tax.getID());
    putIfNotNull(props, NeoProperties.TAXON_ID, tax.getTaxonID());
    putIfNotNull(props, NeoProperties.SCIENTIFIC_NAME, tax.name.getScientificName());
    putIfNotNull(props, NeoProperties.AUTHORSHIP, tax.name.authorshipComplete());
    putIfNotNull(props, NeoProperties.RANK, tax.name.getRank());
    return props;
  }
}

