package life.catalogue.es2;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.IndexConfig;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

/**
 * Helper class to build test data for ES integration tests.
 */
public class TestIndexUtils {

  public static Name parseName(String name, String authorship, Rank rank, NomCode code) throws InterruptedException {
    IssueContainer issues = IssueContainer.simple();
    var opt = NameParser.PARSER.parse(name, authorship, rank, code, issues);
    return opt.get().getName();
  }

  /** Build a taxon wrapper (not yet inserted). */
  public static NameUsageWrapper taxon(String id, String sciName, String authorship,
                                       Rank rank, NomCode code, int datasetKey, Integer sectorKey, String publishedInId) throws Exception {
    Name n = parseName(sciName, authorship, rank, code);
    n.setDatasetKey(datasetKey);
    n.setId(id + "_n");
    if (publishedInId != null) n.setPublishedInId(publishedInId);
    Taxon t = TestEntityGenerator.newTaxon(n, id, null);
    if (sectorKey != null) t.setSectorKey(sectorKey);
    return TestEntityGenerator.newNameUsageWrapper(t);
  }

  public static NameUsageWrapper synonym(String id, String sciName, String authorship, Rank rank, NomCode code,
                             int datasetKey, String acceptedId) throws Exception {
    Name n = parseName(sciName, authorship, rank, code);
    n.setDatasetKey(datasetKey);
    n.setId(id + "_n");
    Synonym s = TestEntityGenerator.newSynonym(TaxonomicStatus.SYNONYM, n, acceptedId);
    s.setId(id);
    return TestEntityGenerator.newNameUsageWrapper(s);
  }

  public static NameUsageWrapper withClassification(List<SimpleName> classification, NameUsageWrapper nuw) {
    if (classification != null) {
      if (nuw.getUsage() instanceof NameUsageBase nub) {
        classification.add(new SimpleName(nub));
      }
      nuw.setClassification(classification);
    }
    return nuw;
  }

  public static NameUsageWrapper withExtinct(NameUsageWrapper nuw, boolean extinct) {
    ((Taxon)nuw.getUsage()).setExtinct(extinct);
    return nuw;
  }

  public static SimpleName insert(ElasticsearchClient c, IndexConfig cfg, NameUsageWrapper w) throws Exception {
    EsUtil.insert(c, cfg.name, w);
    return w.getUsage() instanceof NameUsageBase nub ? new SimpleName(nub) : null;
  }
}
