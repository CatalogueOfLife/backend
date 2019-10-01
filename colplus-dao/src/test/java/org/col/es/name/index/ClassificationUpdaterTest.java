package org.col.es.name.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.SimpleName;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.api.search.NameSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.es.EsReadTestBase;
import org.col.es.EsUtil;
import org.col.es.name.search.NameUsageSearchServiceEs;
import org.elasticsearch.client.RestClient;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.gbif.nameparser.api.Rank.FAMILY;
import static org.gbif.nameparser.api.Rank.GENUS;
import static org.gbif.nameparser.api.Rank.ORDER;
import static org.gbif.nameparser.api.Rank.SPECIES;
import static org.junit.Assert.assertEquals;

public class ClassificationUpdaterTest extends EsReadTestBase {

  private static final int DATASET_KEY = 1000;

  @Test
  public void test1() throws IOException {

    destroyAndCreateIndex();

    RestClient client = getEsClient();

    // Index some test objects
    NameUsageIndexer indexer = new NameUsageIndexer(client, indexName);
    indexer.accept(createTestObjects());
    EsUtil.refreshIndex(client, indexName);

    // Modify the classification of the test objects and run the updater
    List<NameUsageWrapper> nameUsages = createTestObjects();
    nameUsages.forEach(nu -> nu.getClassification().forEach(sn -> sn.setName(sn.getName() + " UPDATED")));
    try (ClassificationUpdater updater = new ClassificationUpdater(indexer, DATASET_KEY)) {
      nameUsages.forEach(updater::handle);
    }
    EsUtil.refreshIndex(client, indexName);

    // Make sure that what ends up in ES equals the modified name usages
    NameUsageSearchServiceEs svc = new NameUsageSearchServiceEs(indexName, client);
    NameSearchRequest query = new NameSearchRequest();
    query.setSortBy(SortBy.NATIVE);
    NameSearchResponse nsr = svc.search(query, new Page());

    assertEquals(nameUsages, nsr.getResult());

  }

  private static List<NameUsageWrapper> createTestObjects() {

    NameUsageWrapper nuw1 = new NameUsageWrapper();
    setClassificationIds(nuw1, Arrays.asList("7", "8", "9", "10"));
    setClassificationRanks(nuw1, Arrays.asList(ORDER, FAMILY, GENUS, SPECIES));
    setClassificationNames(nuw1, Arrays.asList("order_1", "family_1", "genus_1", "order_1"));
    NameUsage nu = new Taxon();
    nu.setId("10");
    Name name = new Name();
    name.setDatasetKey(DATASET_KEY);
    name.setNameIndexId("A control value that should survive the update process unchanged");
    nu.setName(name);
    nuw1.setUsage(nu);

    NameUsageWrapper nuw2 = new NameUsageWrapper();
    setClassificationIds(nuw2, Arrays.asList("17", "18", "19", "20"));
    setClassificationRanks(nuw2, Arrays.asList(ORDER, FAMILY, GENUS, SPECIES));
    setClassificationNames(nuw2, Arrays.asList("order_2", "family_2", "genus_2", "species_2"));
    nu = new Taxon();
    nu.setId("20");
    name = new Name();
    name.setDatasetKey(DATASET_KEY);
    name.setNameIndexId("A control value that should survive the update process unchanged");
    nu.setName(name);
    nuw2.setUsage(nu);

    NameUsageWrapper nuw3 = new NameUsageWrapper();
    setClassificationIds(nuw3, Arrays.asList("17", "18", "19", "20", "777"));
    setClassificationRanks(nuw3, Arrays.asList(ORDER, FAMILY, GENUS, SPECIES, SPECIES));
    setClassificationNames(nuw3, Arrays.asList("order_2", "family_2", "genus_2", "species_2", "synonym_2"));
    nu = new Synonym();
    // Create the most minimalistic taxon that will still make it through the indexing process without NPEs etc.
    Taxon accepted = new Taxon();
    accepted.setName(new Name());
    ((Synonym) nu).setAccepted(accepted);
    nu.setId("777");
    name = new Name();
    name.setDatasetKey(DATASET_KEY);
    name.setNameIndexId("A control value that should survive the update process unchanged");
    nu.setName(name);
    nuw3.setUsage(nu);

    return Arrays.asList(nuw1, nuw2, nuw3);
  }

  // These odd methods exist to bridge legacy code, where ids, names and ranks were set separately:

  private static void setClassificationNames(NameUsageWrapper nuw, List<String> names) {
    if (nuw.getClassification() == null) {
      nuw.setClassification(new ArrayList<>(names.size()));
      SimpleName sn;
      for (int i = 0; i < names.size(); i++) {
        (sn = new SimpleName()).setName(names.get(i));
        nuw.getClassification().add(sn);
      }
    } else {
      for (int i = 0; i < names.size(); i++) {
        nuw.getClassification().get(i).setName(names.get(i));
      }
    }
  }

  private static void setClassificationRanks(NameUsageWrapper nuw, List<Rank> ranks) {
    if (nuw.getClassification() == null) {
      nuw.setClassification(new ArrayList<>(ranks.size()));
      SimpleName sn;
      for (int i = 0; i < ranks.size(); i++) {
        (sn = new SimpleName()).setRank(ranks.get(i));
        nuw.getClassification().add(sn);
      }
    } else {
      for (int i = 0; i < ranks.size(); i++) {
        nuw.getClassification().get(i).setRank(ranks.get(i));
      }
    }
  }

  private static void setClassificationIds(NameUsageWrapper nuw, List<String> ids) {
    if (nuw.getClassification() == null) {
      nuw.setClassification(new ArrayList<>(ids.size()));
      SimpleName sn;
      for (int i = 0; i < ids.size(); i++) {
        (sn = new SimpleName()).setId(ids.get(i));
        nuw.getClassification().add(sn);
      }
    } else {
      for (int i = 0; i < ids.size(); i++) {
        nuw.getClassification().get(i).setId(ids.get(i));
      }
    }
  }

}
