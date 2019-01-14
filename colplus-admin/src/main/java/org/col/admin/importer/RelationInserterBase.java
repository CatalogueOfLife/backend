package org.col.admin.importer;

import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.neo.model.Labels;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.Term;
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
  
  public RelationInserterBase(NeoDb store, Term acceptedTerm, Term parentTerm) {
    this.store = store;
    this.acceptedTerm = acceptedTerm;
    this.parentTerm = parentTerm;
  }
  
  @Override
  public void process(Node n) {
    if (n.hasLabel(Labels.USAGE)) {
      try {
        NeoUsage u = store.usages().objByNode(n);
        if (u.getVerbatimKey() != null) {
          VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
          Node p;
          if (u.isSynonym()) {
            p = usageByID(acceptedTerm, v, u, Issue.ACCEPTED_ID_INVALID);
            if (p != null) {
              store.createSynonymRel(u.node, p);
            } else {
              // if we ain't got no idea of the accepted flag it
              // the orphan synonym usage will be removed later by the normalizer
              v.addIssues(Issue.ACCEPTED_NAME_MISSING);
            }
            
          } else {
            p = usageByID(parentTerm, v, u, Issue.PARENT_ID_INVALID);
            if (p != null) {
              store.assignParent(p, u.node);
            }
          }
          
          processVerbatimUsage(u, v, p);
          
          store.put(v);
        }
      } catch (Exception e) {
        LOG.error("error processing explicit relations for usage {} {}", n, NeoProperties.getRankedUsage(n), e);
      }
    } else if (n.hasLabel(Labels.NAME)){

      try {
        NeoName nn = store.names().objByNode(n);
        if (nn.getVerbatimKey() != null) {
          VerbatimRecord v = store.getVerbatim(nn.getVerbatimKey());
          processVerbatimName(nn, v);
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
  
  /**
   * @param n
   * @param v
   */
  protected void processVerbatimName(NeoName n, VerbatimRecord v) {
    // override to do further processing per name node
  }

  /**
   * Reads a verbatim idTerm that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to lookup the matching node.
   *
   * If a non empty value foreign key existed which cannot be resolved the given invalidIssue is applied.
   *
   * @return queue of potentially split ids with their matching neo node if found, otherwise null
   */
  protected Node usageByID(Term idTerm, VerbatimRecord v, NeoUsage u, Issue invalidIssue) {
    Node n = null;
    final String id = v.getRaw(idTerm);
    if (id != null && !id.equals(u.getId())) {
      n = store.usages().nodeByID(id);
      if (n == null) {
        v.addIssue(invalidIssue);
      }
    }
    return n;
  }
  
  @Override
  public void commitBatch(int counter) {
    if (Thread.interrupted()) {
      LOG.warn("Normalizer interrupted, exit dataset {} early with incomplete parsing", store.getDataset().getKey());
      throw new NormalizationFailedException("Normalizer interrupted");
    }
    LOG.debug("Processed relations for {} nodes", counter);
  }
}
