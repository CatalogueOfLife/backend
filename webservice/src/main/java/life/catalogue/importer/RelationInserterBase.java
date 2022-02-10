package life.catalogue.importer;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.neo.model.*;

import org.gbif.dwc.terms.Term;

import javax.annotation.Nullable;

import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public abstract class RelationInserterBase implements NodeBatchProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(RelationInserterBase.class);
  
  protected final NeoDb store;
  private final Term acceptedTerm;
  private final Term parentTerm;
  private final Term originalNameTerm;

  public RelationInserterBase(NeoDb store, Term acceptedTerm, Term parentTerm, Term originalNameTerm) {
    this.store = store;
    this.acceptedTerm = acceptedTerm;
    this.parentTerm = parentTerm;
    this.originalNameTerm = originalNameTerm;
  }

  /**
   *
   * @param u
   * @param v
   * @return the parent node
   */
  protected Node processUsage(NeoUsage u, VerbatimRecord v) {
    Node p;
    if (u.isSynonym()) {
      p = usageByID(acceptedTerm, v, u, Issue.ACCEPTED_ID_INVALID);
      if (p != null) {
        if (!store.createSynonymRel(u.node, p)) {
          v.addIssue(Issue.ACCEPTED_ID_INVALID);
        }
      } else {
        // if we ain't got no idea of the accepted flag it
        // the orphan synonym usage will be removed later by the normalizer
        v.addIssues(Issue.ACCEPTED_NAME_MISSING);
      }

    } else {
      p = usageByID(parentTerm, v, u, Issue.PARENT_ID_INVALID);
      if (p != null && !p.equals(u.node)) {
        store.assignParent(p, u.node);
      }
    }
    return p;
  }

  @Override
  public void process(Node n) {
    if (n.hasLabel(Labels.USAGE)) {
      try {
        NeoUsage u = store.usages().objByNode(n);
        if (u.getVerbatimKey() != null) {
          VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
          Node p = processUsage(u, v);
          processVerbatimUsage(u, v, p);
          store.put(v);
        }
      } catch (Exception e) {
        LOG.error("error processing explicit relations for usage {} {}", n, NeoProperties.getRankedUsage(n), e);
      }

    } else if (originalNameTerm != null && n.hasLabel(Labels.NAME)){
      try {
        NeoName nn = store.names().objByNode(n);
        if (nn.getVerbatimKey() != null) {
          VerbatimRecord v = store.getVerbatim(nn.getVerbatimKey());
          Node o = nameByID(originalNameTerm, v, nn, Issue.BASIONYM_ID_INVALID);
          if (o != null) {
            NeoRel rel = new NeoRel();
            rel.setType(RelType.HAS_BASIONYM);
            rel.setVerbatimKey(nn.getVerbatimKey());
            store.createNeoRel(nn.node, o, rel);
          }
          store.put(v);
        }
      } catch (Exception e) {
        LOG.error("error processing explicit relations for name {} {}", n, NeoProperties.getScientificNameWithAuthor(n), e);
      }
      
    }
  }
  
  /**
   * @param u
   * @param v
   * @param p parent (usage=taxon) or accepted (usage=synonym) node
   */
  protected void processVerbatimUsage(NeoUsage u, VerbatimRecord v, Node p) {
    // override to do further processing per usage node
  }

  protected Node usageByID(Term idTerm, VerbatimRecord v, NeoUsage u) {
    return usageByID(idTerm, v, u, null);
  }

  /**
   * Reads a verbatim idTerm that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to lookup the matching node.
   *
   * If a non empty value foreign key existed which cannot be resolved the given invalidIssue is applied.
   *
   * @return queue of potentially split ids with their matching neo node if found, otherwise null
   */
  protected Node usageByID(Term idTerm, VerbatimRecord v, NeoUsage u, @Nullable Issue invalidIssue) {
    Node n = null;
    final String id = v.getRaw(idTerm);
    if (id != null && !id.equals(u.getId())) {
      n = store.usages().nodeByID(id);
      if (n == null && invalidIssue != null) {
        v.addIssue(invalidIssue);
      }
    }
    return n;
  }

  protected Node nameByID(Term idTerm, VerbatimRecord v, NeoName nn, Issue invalidIssue) {
    Node n = null;
    final String id = v.getRaw(idTerm);
    if (id != null && !id.equals(nn.getId())) {
      n = store.names().nodeByID(id);
      if (n == null) {
        v.addIssue(invalidIssue);
      }
    }
    return n;
  }
  
  @Override
  public void commitBatch(int counter) {
    if (Thread.interrupted()) {
      LOG.warn("Normalizer interrupted, exit dataset {} early with incomplete parsing", store.getDatasetKey());
      throw new NormalizationFailedException("Normalizer interrupted");
    }
    LOG.debug("Processed relations for {} nodes", counter);
  }
}
