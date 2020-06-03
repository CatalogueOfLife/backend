package life.catalogue.importer.neo;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import life.catalogue.api.model.Name;
import life.catalogue.importer.neo.model.Labels;
import life.catalogue.importer.neo.model.NeoProperties;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.RelType;
import org.neo4j.graphdb.*;

import java.util.Map;

/**
 * Static utils for the NeoDb class
 */
public class NeoDbUtils {
  private final static Joiner LABEL_JOINER = Joiner.on(" ").skipNulls();

  private NeoDbUtils() {
  }
  
  /**
   * @return true if the name node is a basionym
   */
  public static boolean isBasionym(Node nameNode) {
    return nameNode.hasRelationship(RelType.HAS_BASIONYM, Direction.INCOMING);
  }
  
  /**
   * @return true if the usage is a pro parte synoynm with multiple accepted names
   */
  public static boolean isProParteSynonym(Node usageNode) {
    return usageNode.getDegree(RelType.SYNONYM_OF, Direction.OUTGOING) > 1;
  }
  
  /**
   * @return if n is a Name node and used for a Taxon
   */
  public static boolean isAcceptedName(Node nameNode) {
    for (Relationship rel : nameNode.getRelationships(RelType.HAS_NAME, Direction.INCOMING)) {
      if (rel.getStartNode().hasLabel(Labels.TAXON)) {
        return true;
      }
    }
    return false;
  }

  public static String labelsToString(Node n) {
    return LABEL_JOINER.join(n.getLabels());
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
    putIfNotNull(props, NeoProperties.AUTHORSHIP, name.buildAuthorship());
    putIfNotNull(props, NeoProperties.RANK, name.getRank());
    return props;
  }
  
  public static Map<String, Object> neo4jProps(NeoRel rel) {
    return neo4jProps(rel, Maps.newHashMap());
  }
  
  public static <T extends Map<String, Object>> T neo4jProps(NeoRel rel, T props) {
    putIfNotNull(props, NeoProperties.VERBATIM_KEY, rel.getVerbatimKey());
    putIfNotNull(props, NeoProperties.REF_ID, rel.getReferenceId());
    putIfNotNull(props, NeoProperties.NOTE, rel.getRemarks());
    return props;
  }
}

