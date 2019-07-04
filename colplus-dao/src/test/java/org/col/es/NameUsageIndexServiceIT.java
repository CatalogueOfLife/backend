package org.col.es;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.col.api.model.EditorialDecision;
import org.col.api.model.EditorialDecision.Mode;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchResponse;
import org.col.dao.DecisionDao;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;
import org.junit.Test;

import static java.util.stream.Collectors.toList;

import static org.col.db.PgSetupRule.getSqlSessionFactory;
import static org.junit.Assert.assertEquals;

/*
 * Full round-trips into Postgres via DAOs, out of Postgres via the NameUsageWrapperMapper, into Elasticsearch via the NameUsageIndexService
 * and finally out of Elasticsearch via the NameUsageSearchService. We have to massage the in-going out-going name usages slightly to allow
 * them to be compared (e.g. because the recursive query we execute in Postgres cannot be emulated with Elasticsearch), but not much.
 */
public class NameUsageIndexServiceIT extends EsReadWriteTestBase {

  @Test
  public void indexDatasetTaxaOnly() throws IOException {
    List<Taxon> pgTaxa = createPgTaxa(7);
    createIndexService().indexDataset(EsSetupRule.DATASET_KEY);
    List<String> ids = pgTaxa.stream().map(Taxon::getId).collect(toList());
    NameSearchResponse res = query(new TermsQuery("usageId", ids));
    List<Taxon> esTaxa = res.getResult().stream().map(nuw -> (Taxon) nuw.getUsage()).collect(toList());
    massageTaxa(pgTaxa);
    massageTaxa(esTaxa);
    assertEquals(pgTaxa, esTaxa);
  }

  @Test
  public void makeEditorialDecision() throws IOException {
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(3);
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    Taxon edited = pgTaxa.get(0);
    edited.getName().setScientificName("CHANGED");
    EditorialDecision ed = new EditorialDecision();
    ed.setMode(Mode.UPDATE);
    ed.setDatasetKey(edited.getDatasetKey());
    ed.setSubject(SimpleName.of(edited));
    ed.setCreatedBy(edited.getCreatedBy());
    ed.setModifiedBy(edited.getCreatedBy());
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc);
    int decisionKey = dao.create(ed, edited.getCreatedBy()); // this triggers indexTaxa on the index service
    NameSearchResponse res = query(new TermQuery("decisionKey", decisionKey));
    assertEquals(1, res.getResult().size());
    assertEquals(edited.getId(),res.getResult().get(0).getUsage().getId());
  }

  private static void massageTaxa(List<Taxon> taxa) {
    // Cannot compare created and modified fields (probably current time when null)
    taxa.forEach(t -> {
      t.setCreated(null);
      t.setModified(null);
      t.getName().setCreated(null);
      t.getName().setModified(null);
    });
    // The order in which taxa flow from pg to es is impossible to reproduce with an es query, so just re-order by id
    taxa.sort(Comparator.comparing(Taxon::getId));
  }

}
