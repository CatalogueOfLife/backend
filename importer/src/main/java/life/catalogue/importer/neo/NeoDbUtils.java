package life.catalogue.importer.neo;

import ch.qos.logback.core.spi.PropertyContainer;

import life.catalogue.api.model.Name;
import life.catalogue.importer.neo.model.Labels;
import life.catalogue.importer.neo.model.NeoProperties;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.RelType;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.neo4j.graphdb.*;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;

import org.neo4j.internal.helpers.collection.Iterators;

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
    return nameNode.hasRelationship(Direction.INCOMING, RelType.HAS_BASIONYM);
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
    for (Relationship rel : nameNode.getRelationships(Direction.INCOMING, RelType.HAS_NAME)) {
      if (rel.getStartNode().hasLabel(Labels.TAXON)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the given iterable's first element or {@code null} if no
   * element found.
   * <p>
   * If the {@code iterable} implements {@link Resource}, then it will be closed in a {@code finally} block
   * after the first item has been retrieved, or failed to be retrieved.
   * <p>
   * If the {@link Iterable#iterator() iterator} created by the {@code iterable} implements {@link Resource}
   * it will be {@link Resource#close() closed} in a {@code finally} block after the single item
   * has been retrieved, or failed to be retrieved.
   *
   * @param <T> the type of elements in {@code iterable}.
   * @param iterable the {@link Iterable} to get elements from.
   * @return the first element in the {@code iterable}, or {@code null} if no
   * element found.
   */
  public static <T> T firstOrNull(Iterable<T> iterable) {
    try {
      return Iterators.firstOrNull(iterable.iterator());
    } finally {
      tryCloseResource(iterable);
    }
  }

  public static <T> T lastOrNull(Iterable<T> iterable) {
    try {
      return Iterators.firstOrNull(iterable.iterator());
    } finally {
      tryCloseResource(iterable);
    }
  }
  /**
   * Returns the given iterator's single element or {@code null} if no
   * element found. If there is more than one element in the iterator a
   * {@link NoSuchElementException} will be thrown.
   *
   * If the {@code iterator} implements {@link Resource} it will be {@link Resource#close() closed}
   * in a {@code finally} block after the single item has been retrieved, or failed to be retrieved.
   *
   * @param <T> the type of elements in {@code iterator}.
   * @param iterator the {@link Iterator} to get elements from.
   * @return the single element in {@code iterator}, or {@code null} if no
   * element found.
   * @throws NoSuchElementException if more than one element was found.
   */
  public static <T> T singleOrNull(Iterator<T> iterator) {
    try {
      return single(iterator, null);
    } finally {
      tryCloseResource(iterator);
    }
  }

  /**
   * Returns the given iterator's single element or {@code itemIfNone} if no
   * element found. If there is more than one element in the iterator a
   * {@link NoSuchElementException} will be thrown.
   *
   * If the {@code iterator} implements {@link Resource} it will be {@link Resource#close() closed}
   * in a {@code finally} block after the single item has been retrieved, or failed to be retrieved.
   *
   * @param <T> the type of elements in {@code iterator}.
   * @param iterator the {@link Iterator} to get elements from.
   * @param itemIfNone item to use if none is found
   * @return the single element in {@code iterator}, or {@code itemIfNone} if no
   * element found.
   * @throws NoSuchElementException if more than one element was found.
   */
  public static <T> T single(Iterator<T> iterator, T itemIfNone) {
    try {
      T result = iterator.hasNext() ? iterator.next() : itemIfNone;
      if (iterator.hasNext()) {
        throw new NoSuchElementException("More than one element in " + iterator + ". First element is '"
          + result + "' and the second element is '" + iterator.next() + "'");
      }
      return result;
    } finally {
      tryCloseResource(iterator);
    }
  }

  /**
   * Convenience method for looping over an {@link Iterator}. Converts the
   * {@link Iterator} to an {@link Iterable} by wrapping it in an
   * {@link Iterable} that returns the {@link Iterator}. It breaks the
   * contract of {@link Iterable} in that it returns the supplied iterator
   * instance for each call to {@code iterator()} on the returned
   * {@link Iterable} instance. This method exists to make it easy to use an
   * {@link Iterator} in a for-loop.
   *
   * @param <T> the type of items in the iterator.
   * @param iterator the iterator to expose as an {@link Iterable}.
   * @return the supplied iterator posing as an {@link Iterable}.
   */
  public static <T> Iterable<T> loop(final Iterator<T> iterator) {
    return () -> iterator;
  }

  /**
   * Close the provided {@code iterable} if it implements {@link Resource}.
   *
   * @param iterable the iterable to check for closing
   */
  public static void tryCloseResource(Iterable<?> iterable) {
    if (iterable instanceof Resource closeable) {
      closeable.close();
    }
  }

  /**
   * Close the provided {@code iterator} if it implements {@link Resource}.
   *
   * @param iterator the iterator to check for closing
   */
  public static void tryCloseResource(Iterator<?> iterator) {
    if (iterator instanceof Resource closeable) {
      closeable.close();
    }
  }

  public static <T> ResourceIterable<T> resourceIterable(Iterable<T> iterable) {
    return new ResourceIterable<T>() {
      @Override
      public ResourceIterator<T> iterator() {
        var iter = iterable.iterator();
        return new ResourceIterator<T>() {
          @Override
          public void close() {

          }

          @Override
          public boolean hasNext() {
            return iter.hasNext();
          }

          @Override
          public T next() {
            return iter.next();
          }
        };
      }

      @Override
      public void close() {
        //nothing
      }
    };
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
  static void addProperties(Entity n, Map<String, Object> props) {
    if (props != null) {
      for (Map.Entry<String, Object> p : props.entrySet()) {
        setProperty(n, p.getKey(), p.getValue());
      }
    }
  }

  /**
   * Sets a node property and removes it in case the property value is null.
   */
  static void setProperty(Entity n, String property, Object value) {
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
    putIfNotNull(props, NeoProperties.AUTHORSHIP, name.getAuthorship());
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
    putIfNotNull(props, NeoProperties.SCINAME, rel.getRelatedScientificName());
    return props;
  }
}

