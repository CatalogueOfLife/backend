package life.catalogue.es.nu;

import life.catalogue.api.model.DSID;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.db.TestDataRule;
import life.catalogue.es.EsPgTestBase;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/*
 * Tests sector indexing, especially merge ones, using a pre-generated data rule, see TestDataGenerator
 */
public class NameUsageIndexServiceEsSectorIT extends EsPgTestBase {

  @Rule
  public final TestDataRule testDataRule = TestDataRule.colSynced();

  @Test
  public void indexAll() throws IOException {
    createIndexService().indexAll();
    var req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, Datasets.COL);
    NameUsageSearchResponse res = search(req);
    assertEquals(33, res.size());
    int taxCounter = 0;
    for (var nuw : res.getResult()) {
      if (nuw.getUsage().isTaxon()) {
        taxCounter++;
        assertTrue(nuw.getClassification().size() > 0);
      }
    }
    assertEquals(27, taxCounter);
  }

  @Test
  public void indexMergeSector() throws IOException {
    createIndexService().indexDataset(Datasets.COL);

    final DSID<Integer> skey = DSID.of(Datasets.COL, 2);

    var req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, Datasets.COL);
    req.addFilter(NameUsageSearchParameter.SECTOR_KEY, skey.getId());
    NameUsageSearchResponse res = search(req);
    //assertEquals(4, res.size());
    int taxCounter = 0;
    for (var nuw : res.getResult()) {
      if (nuw.getUsage().isTaxon()) {
        taxCounter++;
        assertTrue(nuw.getClassification().size() > 2);
      }
    }
    assertEquals(3, taxCounter);

    createIndexService().indexSector(skey);
    res = search(req);
    taxCounter = 0;
    for (var nuw : res.getResult()) {
      if (nuw.getUsage().isTaxon()) {
        taxCounter++;
        assertTrue(nuw.getClassification().size() > 2);
      }
    }
    assertEquals(3, taxCounter);

  }

}
