package life.catalogue.es.search;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsTestBase;
import life.catalogue.es.EsUtil;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import static life.catalogue.es.TestIndexUtils.taxon;
import static org.junit.Assert.assertEquals;

/**
 * Integration test for {@link NameUsageSearchServiceEs#scroll} verifying it drains the full result set
 * across several scroll batches.
 */
public class NameUsageScrollIT extends EsTestBase {

  private static final int DS = 100;

  @Test
  public void scrollAll() throws Exception {
    // index 7 taxa in two datasets
    for (int i = 1; i <= 5; i++) {
      EsUtil.insert(client, cfg.index.name, taxon("t" + i, "Genus" + i, "Auth, 2020", Rank.GENUS, NomCode.ZOOLOGICAL, DS, null, null));
    }
    EsUtil.insert(client, cfg.index.name, taxon("o1", "Other one", null, Rank.GENUS, NomCode.ZOOLOGICAL, 200, null, null));
    EsUtil.insert(client, cfg.index.name, taxon("o2", "Other two", null, Rank.GENUS, NomCode.ZOOLOGICAL, 200, null, null));
    EsUtil.refreshIndex(client, cfg.index.name);

    var service = new NameUsageSearchServiceEs(cfg.index.name, client);

    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, DS);

    // a tiny batch size forces several scroll batches for the 5 matching docs
    List<NameUsageWrapper> collected = new ArrayList<>();
    service.scroll(req, 2, collected::add);

    assertEquals(5, collected.size());
    var ids = collected.stream().map(NameUsageWrapper::getId).collect(Collectors.toSet());
    assertEquals(java.util.Set.of("t1", "t2", "t3", "t4", "t5"), ids);

    // unfiltered scroll returns everything across both datasets
    List<NameUsageWrapper> all = new ArrayList<>();
    service.scroll(new NameUsageSearchRequest(), 3, all::add);
    assertEquals(7, all.size());
  }
}
