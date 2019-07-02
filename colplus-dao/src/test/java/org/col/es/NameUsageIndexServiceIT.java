package org.col.es;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.col.api.model.Taxon;
import org.col.api.search.NameSearchResponse;
import org.col.es.query.TermsQuery;
import org.junit.Test;

import static java.util.stream.Collectors.toList;

import static org.junit.Assert.assertEquals;

public class NameUsageIndexServiceIT extends EsReadWriteTestBase {

  /*
   * Full round-trip into Postgres, out of Postgres via the NameUsageWrapperMapper, into Elasticsearch via the NameUsageIndexService, out of
   * Elasticsearch via the NameUsageSearchService, and then comparing input and output. We have to massage input and output a little bit to
   * make it work, but not much.
   */
  @Test
  public void indexDataset01() throws IOException {
    List<Taxon> pgTaxa = createPgTaxa(7);
    createIndexService().indexDataset(EsSetupRule.DATASET_KEY);
    List<String> ids = pgTaxa.stream().map(Taxon::getId).collect(toList());
    NameSearchResponse res = query(new TermsQuery("usageId", ids));
    List<Taxon> esTaxa = res.getResult().stream().map(nuw -> (Taxon) nuw.getUsage()).collect(toList());
    // Can't compare created and modified fields apparently
    pgTaxa.forEach(t -> {
      t.setCreated(null);
      t.setModified(null);
    });
    esTaxa.forEach(t -> {
      t.setCreated(null);
      t.setModified(null);
    });
    // Let's not make assumptions about the order of taxa flowing from pg to es.
    pgTaxa.sort(Comparator.comparing(Taxon::getId));
    esTaxa.sort(Comparator.comparing(Taxon::getId));
    assertEquals(pgTaxa, esTaxa);
  }

}
