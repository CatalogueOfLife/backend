package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.api.search.NameSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.db.PgSetupRule;
import org.elasticsearch.client.RestClient;
import org.gbif.nameparser.api.Rank;
import org.junit.Ignore;
import org.junit.Test;

import static org.gbif.nameparser.api.Rank.CLASS;
import static org.gbif.nameparser.api.Rank.FAMILY;
import static org.gbif.nameparser.api.Rank.GENUS;
import static org.gbif.nameparser.api.Rank.KINGDOM;
import static org.gbif.nameparser.api.Rank.ORDER;
import static org.gbif.nameparser.api.Rank.PHYLUM;
import static org.gbif.nameparser.api.Rank.SPECIES;
import static org.junit.Assert.assertEquals;

@Ignore("Its too slow for jenkins most of the time")
public class NameUsageIndexServiceESTest extends EsReadWriteTestBase {

  @Test // Nice in combination with PgImportIT.testGsdGithub
  @Ignore
  public void indexDataSet() throws IOException, EsException {
    try (RestClient client = getEsClient()) {
      NameUsageIndexServiceES svc = new NameUsageIndexServiceES(client, getEsConfig(), factory());
      svc.indexDataset(1000);
    }
  }

  @Test
  public void indexWithClassification() throws IOException, EsException {

    try (RestClient client = getEsClient()) {
      final String index = "name_usage_test";
      EsUtil.deleteIndex(client, index);
      EsUtil.createIndex(client, index, getEsConfig().nameUsage);
      NameUsageIndexer indexer = new NameUsageIndexer(client, index);
      indexer.accept(createTaxa());
      EsUtil.refreshIndex(client, index);
      try (SynonymResultHandler srh = new SynonymResultHandler(indexer, 42)) {
        for (NameUsageWrapper nuw : createSynonyms()) {
          srh.handle(nuw);
        }
      }
      EsUtil.refreshIndex(client, index);

      NameSearchRequest nsr = new NameSearchRequest();
      nsr.setSortBy(SortBy.NATIVE);
      NameUsageSearchService svc = new NameUsageSearchService(index, client);
      NameSearchResponse response = svc.search(index, nsr, new Page());
      List<NameUsageWrapper> result = response.getResult();

      /*
       * We can compare ingoing taxa with outgoing taxa (including classifications), but we must create the taxa afresh, because the
       * original ingoing taxa will have been pruned after entering Elasticsearch.
       */
      List<NameUsageWrapper> taxa = createTaxa();
      assertEquals(taxa.get(0), result.get(0));
      assertEquals(taxa.get(1), result.get(1));

      /*
       * We can't compare ingoing synonyms with outgoing synonyms, because the synonyms will have acquired their taxon's classifications
       * upon entering Elasticsearch.
       */
      assertEquals(taxa.get(0).getClassification(), result.get(2).getClassification()); // syn1 got classification from t1
      assertEquals(taxa.get(0).getClassification(), result.get(3).getClassification()); // syn2 got classification from t1
      assertEquals(taxa.get(1).getClassification(), result.get(4).getClassification()); // syn3 got classification from t2
    }

  }

  private static List<NameUsageWrapper> createTaxa() {
    List<String> ids1 = Arrays.asList("t1", "t2", "t3", "t4", "t5a", "t6a", "t7a");
    List<String> names1 = Arrays.asList(
        "KINGDOM 1",
        "PHYLUM 1",
        "CLASS 1",
        "ORDER 1",
        "FAMILY 1",
        "GENUS 1",
        "SPECIES 1");
    List<Rank> ranks1 = Arrays.asList(
        KINGDOM,
        PHYLUM,
        CLASS,
        ORDER,
        FAMILY,
        GENUS,
        SPECIES);

    List<String> ids2 = Arrays.asList("t1", "t2", "t3", "t4", "t5b", "t6b", "t7b");
    List<String> names2 = Arrays.asList(
        "KINGDOM 1",
        "PHYLUM 1",
        "CLASS 1",
        "ORDER 1",
        "FAMILY 2",
        "GENUS 2",
        "SPECIES 2");
    List<Rank> ranks2 = Arrays.asList(
        KINGDOM,
        PHYLUM,
        CLASS,
        ORDER,
        FAMILY,
        GENUS,
        SPECIES);

    Name n = new Name();
    n.setDatasetKey(42);
    n.setRank(Rank.SPECIES);
    n.setScientificName("SPECIES 1");
    Taxon taxon1 = new Taxon();
    taxon1.setId("t7a");
    taxon1.setName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper();
    nuw1.setUsage(taxon1);
    nuw1.setClassificationIds(ids1);
    nuw1.setClassificationNames(names1);
    nuw1.setClassificationRanks(ranks1);

    n = new Name();
    n.setDatasetKey(42);
    n.setRank(Rank.SPECIES);
    n.setScientificName("SPECIES 2");
    Taxon taxon2 = new Taxon();
    taxon2.setId("t7b");
    taxon2.setName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper();
    nuw2.setUsage(taxon2);
    nuw2.setClassificationIds(ids2);
    nuw2.setClassificationNames(names2);
    nuw2.setClassificationRanks(ranks2);

    return Arrays.asList(nuw1, nuw2);
  }

  private static List<NameUsageWrapper> createSynonyms() {

    /*
     * Watch out for nasty gotcha. We can't reuse the ones created in createTaxa() because they will will have been pruned after entering
     * Elasticsearch.
     */

    Name n = new Name();
    n.setDatasetKey(42);
    n.setRank(Rank.SPECIES);
    n.setScientificName("SPECIES 1");
    Taxon taxon1 = new Taxon();
    taxon1.setId("t7a");
    taxon1.setName(n);

    n = new Name();
    n.setDatasetKey(42);
    n.setRank(Rank.SPECIES);
    n.setScientificName("SPECIES 2");
    Taxon taxon2 = new Taxon();
    taxon2.setId("t7b");
    taxon2.setName(n);

    n = new Name();
    n.setDatasetKey(42);
    n.setRank(Rank.SPECIES);
    n.setScientificName("SYNONYM 1");
    Synonym syn1 = new Synonym();
    syn1.setId("s1");
    syn1.setName(n);
    syn1.setAccepted(taxon1);
    NameUsageWrapper nuw1 = new NameUsageWrapper();
    nuw1.setUsage(syn1);

    n = new Name();
    n.setDatasetKey(42);
    n.setRank(Rank.SPECIES);
    n.setScientificName("SYNONYM 2");
    Synonym syn2 = new Synonym();
    syn2.setId("s2");
    syn2.setName(n);
    syn2.setAccepted(taxon1);
    NameUsageWrapper nuw2 = new NameUsageWrapper();
    nuw2.setUsage(syn2);

    n = new Name();
    n.setDatasetKey(42);
    n.setRank(Rank.SPECIES);
    n.setScientificName("SYNONYM 3");
    Synonym syn3 = new Synonym();
    syn3.setId("s3");
    syn3.setName(n);
    syn3.setAccepted(taxon2);
    NameUsageWrapper nuw3 = new NameUsageWrapper();
    nuw3.setUsage(syn3);

    return Arrays.asList(nuw1, nuw2, nuw3);
  }

  private static SqlSessionFactory factory() {
    return PgSetupRule.getSqlSessionFactory();
  }

}
