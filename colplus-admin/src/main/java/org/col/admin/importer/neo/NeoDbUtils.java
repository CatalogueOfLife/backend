package org.col.admin.importer.neo;

import java.util.Map;

import com.google.common.collect.Maps;
import org.col.admin.importer.neo.model.NeoNameRel;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.api.model.Name;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;

/**
 * Static utils for the NeoDb class
 */
public class NeoDbUtils {

  private NeoDbUtils() {
  }

  private static void putIfNotNull(Map<String, Object> props, String property, String value) {
    if (value != null) {
      props.put(property, value);
    }
  }
  
  private static void putIfNotNull(Map<String, Object> props, String property, Enum value) {
    if (value != null) {
      props.put(property, value.ordinal());
    }
  }
  
  private static void putIfNotNull(Map<String, Object> props, String property, Integer value) {
    if (value != null) {
      props.put(property, value);
    }
  }

  /**
   * Adds node properties and removes them in case the property value is null.
   */
  static void addProperties(PropertyContainer n, Map<String, Object> props) {
    if (props != null) {
      for (Map.Entry<String, Object> p : props.entrySet()) {
        setProperty(n, p.getKey(), p.getValue());
      }
    }
  }

  /**
   * Sets a node property and removes it in case the property value is null.
   */
  static void setProperty(PropertyContainer n, String property, Object value) {
    if (value == null) {
      n.removeProperty(property);
    } else {
      n.setProperty(property, value);
    }
  }
  
  /**
   * Remove all node labels
   */
  static void removeLabels(Node n) {
    for (Label l : n.getLabels()) {
      n.removeLabel(l);
    }
  }
  
  /**
   * Adds new labels to a node
   */
  static void addLabels(Node n, Label... labels ) {
    if (labels != null) {
      for (Label l : labels) {
        n.addLabel(l);
      }
    }
  }
  
  public static Map<String, Object> neo4jProps(Name name) {
    return neo4jProps(name, Maps.newHashMap());
  }
  
  public static <T extends Map<String, Object>> T neo4jProps(Name name, T props) {
    putIfNotNull(props, NeoProperties.SCIENTIFIC_NAME, name.getScientificName());
    putIfNotNull(props, NeoProperties.AUTHORSHIP, name.authorshipComplete());
    putIfNotNull(props, NeoProperties.RANK, name.getRank());
    return props;
  }
  
  public static Map<String, Object> neo4jProps(NeoNameRel rel) {
    return neo4jProps(rel, Maps.newHashMap());
  }
  
  public static <T extends Map<String, Object>> T neo4jProps(NeoNameRel rel, T props) {
    putIfNotNull(props, NeoProperties.VERBATIM_KEY, rel.getVerbatimKey());
    putIfNotNull(props, NeoProperties.REF_ID, rel.getRefId());
    putIfNotNull(props, NeoProperties.NOTE, rel.getNote());
    return props;
  }
}

