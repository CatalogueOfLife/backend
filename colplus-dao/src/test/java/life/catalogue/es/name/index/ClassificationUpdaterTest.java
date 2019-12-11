package life.catalogue.es.name.index;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SortBy;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.EsUtil;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gbif.nameparser.api.Rank.*;
import static org.junit.Assert.assertEquals;

public class ClassificationUpdaterTest extends EsReadTestBase {

  private static final int DATASET_KEY = 1000;

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() throws IOException {

    // Index some test objects
    index(createTestObjects());

    NameUsageIndexer indexer = new NameUsageIndexer(getEsClient(), indexName);

    // Modify the classification of the test objects and run the updater
    // Always create wrapper objects afresh b/c they will be pruned upon insert
    List<NameUsageWrapper> nameUsages = createTestObjects();
    nameUsages.forEach(nu -> nu.getClassification().forEach(sn -> sn.setName(sn.getName() + " (updated name)")));
    new ClassificationUpdater(indexer, DATASET_KEY).accept(nameUsages);
    EsUtil.refreshIndex(getEsClient(), indexName);

    // Always create wrapper objects afresh b/c they will be pruned upon insert
    List<NameUsageWrapper> expected = createTestObjects();
    expected.forEach(nu -> nu.getClassification().forEach(sn -> sn.setName(sn.getName() + " (updated name)")));

    // Make sure that what ends up in ES equals the modified nam usages
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSortBy(SortBy.NATIVE);
    List<NameUsageWrapper> actual = search(query).getResult();

    assertEquals(expected, actual);
  }

  private static List<NameUsageWrapper> createTestObjects() {

    NameUsageWrapper nuw1 = new NameUsageWrapper();
    nuw1.setClassification(createClassification(
        "7",
        ORDER,
        "order_1",
        "8",
        FAMILY,
        "family_1",
        "9",
        GENUS,
        "genus_1",
        "10",
        SPECIES,
        "order_1"));
    NameUsage nu = new Taxon();
    nu.setId("10");
    nuw1.setId("10");
    Name name = new Name();
    name.setDatasetKey(DATASET_KEY);
    name.setNameIndexId("A control value that should survive the update process unchanged");
    nu.setName(name);
    nuw1.setUsage(nu);

    NameUsageWrapper nuw2 = new NameUsageWrapper();
    nuw2.setClassification(createClassification(
        "17",
        ORDER,
        "order_2",
        "18",
        FAMILY,
        "family_2",
        "19",
        GENUS,
        "genus_2",
        "20",
        SPECIES,
        "species_2"));
    nu = new Taxon();
    nu.setId("20");
    nuw2.setId("20");
    name = new Name();
    name.setDatasetKey(DATASET_KEY);
    name.setNameIndexId("A control value that should survive the update process unchanged");
    nu.setName(name);
    nuw2.setUsage(nu);

    NameUsageWrapper nuw3 = new NameUsageWrapper();
    nuw3.setClassification(createClassification(
        "17",
        ORDER,
        "order_2",
        "18",
        FAMILY,
        "family_2",
        "19",
        GENUS,
        "genus_2",
        "20",
        SPECIES,
        "species_2",
        "777",
        SPECIES,
        "synonym_2"));
    nu = new Synonym();
    // The most minimalistic taxon that will still make it through the indexing process without NPEs etc.
    Taxon accepted = new Taxon();
    accepted.setName(new Name());
    ((Synonym) nu).setAccepted(accepted);
    nu.setId("777");
    nuw3.setId("777");
    name = new Name();
    name.setDatasetKey(DATASET_KEY);
    name.setNameIndexId("A control value that should survive the update process unchanged");
    nu.setName(name);
    nuw3.setUsage(nu);

    return Arrays.asList(nuw1, nuw2, nuw3);
  }

  private static List<SimpleName> createClassification(Object... data) {
    List<SimpleName> cl = new ArrayList<>();
    for (int i = 0; i < data.length; i = i + 3) {
      SimpleName sn = new SimpleName();
      sn.setId((String) data[i]);
      sn.setRank((Rank) data[i + 1]);
      sn.setName((String) data[i + 2]);
      cl.add(sn);
    }
    return cl;
  }

}
