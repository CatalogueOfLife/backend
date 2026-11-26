package life.catalogue.importer;

import life.catalogue.api.model.LinneanNameUsage;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.dao.ParentStack;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.TreeWalker;
import life.catalogue.importer.store.model.NameUsageData;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives an accepted infrageneric name without a genus the genus of its classification, the closest accepted genus
 * above it, so a subgenus given as the bare uninomial Ctenobelba becomes Ctenobelba (Ctenobelba) and a botanical
 * section Cracca becomes Vicia sect. Cracca. The botanical code requires the genus (ICN Art. 21.1), Darwin Core asks
 * for it in dwc:subgenus, and a nominotypical subgenus is otherwise indistinguishable from its genus.
 * Synonyms are left alone, their accepted name says nothing about the genus they were placed in, and so is a name
 * that already has a genus, even one that differs from its classification. The validator flags those.
 * See https://github.com/CatalogueOfLife/checklistbank/issues/1763
 */
public class InfragenericGenusAssigner implements TreeWalker.StartEndHandler {
  private static final Logger LOG = LoggerFactory.getLogger(InfragenericGenusAssigner.class);
  private final ImportStore store;
  private final ParentStack<LinneanNameUsage> parents = new ParentStack<>();
  private int assigned;

  public InfragenericGenusAssigner(ImportStore store) {
    this.store = store;
  }

  @Override
  public void start(NameUsageData nu, TreeWalker.WalkerContext ctxt) {
    if (!nu.ud.isTaxon()) {
      return;
    }
    parents.push(new LinneanNameUsage(nu.toNameUsageBase()));
    final Name n = nu.nd.getName();
    if (lacksGenus(n)) {
      var genus = parents.getByRank(Rank.GENUS);
      if (genus != null && genus.isParsed() && genus.getType() == NameType.SCIENTIFIC && genus.getUninomial() != null) {
        n.setGenus(genus.getUninomial());
        n.rebuildScientificName();
        store.names().update(nu.nd);
        if (n.getVerbatimKey() != null) {
          VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
          v.add(Issue.GENUS_FROM_CLASSIFICATION);
          store.put(v);
        }
        assigned++;
      }
    }
  }

  static boolean lacksGenus(Name n) {
    return n.isParsed()
      && n.getType() == NameType.SCIENTIFIC
      && n.getRank() != null && n.getRank().isInfragenericStrictly()
      && n.getGenus() == null
      && n.getInfragenericEpithet() != null
      && n.getSpecificEpithet() == null;
  }

  @Override
  public void end(NameUsageData data, TreeWalker.WalkerContext ctxt) {
  }

  public void logStats() {
    LOG.info("{} infrageneric names got the genus of their classification", assigned);
  }
}
