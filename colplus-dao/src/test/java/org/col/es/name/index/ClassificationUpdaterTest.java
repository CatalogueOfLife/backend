package org.col.es.name.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.SimpleName;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.api.search.NameUsageWrapper;
import org.col.es.EsReadTestBase;
import org.col.es.EsUtil;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.gbif.nameparser.api.Rank.FAMILY;
import static org.gbif.nameparser.api.Rank.GENUS;
import static org.gbif.nameparser.api.Rank.ORDER;
import static org.gbif.nameparser.api.Rank.SPECIES;
import static org.junit.Assert.assertEquals;

@Ignore
public class ClassificationUpdaterTest extends EsReadTestBase {

  private static final int DATASET_KEY = 1000;

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() throws IOException {

    index(createTestObjects());

    // Index some test objects
    NameUsageIndexer indexer = new NameUsageIndexer(getEsClient(), indexName);

    // Modify the classification of the test objects and run the updater
    List<NameUsageWrapper> nameUsages = createTestObjects();
    nameUsages.forEach(nu -> nu.getClassification().forEach(sn -> sn.setName(sn.getName() + " UPDATED")));
    try (ClassificationUpdater updater = new ClassificationUpdater(indexer, DATASET_KEY)) {
      nameUsages.forEach(updater::handle);
    }
    EsUtil.refreshIndex(getEsClient(), indexName);

    // Make sure that what ends up in ES equals the modified nam usages
    NameSearchRequest query = new NameSearchRequest();
    query.setSortBy(SortBy.NATIVE);

    assertEquals(createTestObjects(), search(query).getResult());

  }

  private static List<SimpleName> createClassification(Object... data) {
    List<SimpleName> cl = new ArrayList<>();
    for (int i = 0; i < data.length; i = i + 3) {
      SimpleName sn = new SimpleName();
      sn.setId((String) data[i]);
      sn.setRank((Rank) data[i + 1]);
      sn.setName((String) data[i + 2]);
    }
    return cl;
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
    // Create the most minimalistic taxon that will still make it through the indexing process without NPEs etc.
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

}
