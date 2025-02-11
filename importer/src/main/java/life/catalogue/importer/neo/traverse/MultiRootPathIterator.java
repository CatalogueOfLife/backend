package life.catalogue.importer.neo.traverse;

import java.util.Iterator;
import java.util.List;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.traversal.TraversalDescription;

/**
 * Path iterator that traverses multiple start nodes in a given traversal description.
 */
public class MultiRootPathIterator extends MultiRooIterator<Path> {
  
  private final TraversalDescription td;
  
  private MultiRootPathIterator(List<Node> roots, TraversalDescription td) {
    super(roots);
    this.td = td;
    prefetch();
  }
  
  public static ResourceIterable<Path> create(final List<Node> roots, final TraversalDescription td) {
    return new ResourceIterable<>() {
      MultiRootPathIterator iter;
      @Override
      public void close() {
        iter.close();
      }

      @Override
      public ResourceIterator<Path> iterator() {
        iter = new MultiRootPathIterator(roots, td);
        return iter;
      }
    };
  }

  @Override
  Iterator<Path> iterateRoot(Node root) {
    return td.traverse(root).iterator();
  }

  @Override
  public void close() {
    // nothing.
  }
}
