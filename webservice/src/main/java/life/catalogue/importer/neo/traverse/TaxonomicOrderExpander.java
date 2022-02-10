package life.catalogue.importer.neo.traverse;

import life.catalogue.importer.neo.NeoDbUtils;
import life.catalogue.importer.neo.model.RelType;

import java.util.List;

import javax.annotation.Nullable;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.BranchState;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

/**
 * depth first, rank then scientific name order based branching.
 */
public class TaxonomicOrderExpander implements PathExpander {
  /**
   * Expander following the parent_of relations in taxonomic order
   */
  public final static TaxonomicOrderExpander TREE_EXPANDER = new TaxonomicOrderExpander(false);
  
  /**
   * Expander following the parent_of and synonym_of relations in taxonomic order
   */
  public final static TaxonomicOrderExpander TREE_WITH_SYNONYMS_EXPANDER = new TaxonomicOrderExpander(true);
  
  private final boolean includeSynonyms;
  
  private static final Ordering<Relationship> CHILDREN_ORDER = Ordering.from(new TaxonomicOrder()).onResultOf(
      new Function<Relationship, Node>() {
        @Nullable
        @Override
        public Node apply(Relationship rel) {
          return rel.getEndNode();
          
        }
      }
  );
  
  private static final Ordering<Relationship> SYNONYM_ORDER = Ordering.natural().reverse().onResultOf(
      new Function<Relationship, Boolean>() {
        @Nullable
        @Override
        public Boolean apply(Relationship rel) {
          return NeoDbUtils.isBasionym(rel.getStartNode().getSingleRelationship(RelType.HAS_NAME, Direction.OUTGOING).getEndNode());
        }
      }
  ).compound(
      Ordering.from(new TaxonomicOrder()).onResultOf(
          new Function<Relationship, Node>() {
            @Nullable
            @Override
            public Node apply(Relationship rel) {
              return rel.getStartNode();
              
            }
          }
      )
  );
  
  private TaxonomicOrderExpander(boolean includeSynonyms) {
    this.includeSynonyms = includeSynonyms;
  }
  
  @Override
  public Iterable<Relationship> expand(Path path, BranchState state) {
    List<Relationship> children = CHILDREN_ORDER.sortedCopy(path.endNode().getRelationships(RelType.PARENT_OF, Direction.OUTGOING));
    if (includeSynonyms) {
      Iterable<Relationship> synResults = path.endNode().getRelationships(RelType.SYNONYM_OF, Direction.INCOMING);
      return Iterables.concat(
          SYNONYM_ORDER.sortedCopy(synResults),
          children
      );
    } else {
      return children;
    }
  }
  
  @Override
  public PathExpander reverse() {
    throw new UnsupportedOperationException();
  }
  
}
