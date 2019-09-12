package org.col.es.name;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.model.EditorialDecision;
import org.col.api.model.EditorialDecision.Mode;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchResponse;
import org.col.dao.DecisionDao;
import org.col.es.EsModule;
import org.col.es.EsReadWriteTestBase;
import org.col.es.EsSetupRule;
import org.col.es.dsl.TermQuery;
import org.col.es.dsl.TermsQuery;
import org.col.es.name.index.NameUsageIndexService;
import org.gbif.nameparser.api.Rank;
import org.junit.Ignore;
import org.junit.Test;

import static java.util.stream.Collectors.toList;

import static org.col.db.PgSetupRule.getSqlSessionFactory;
import static org.junit.Assert.*;

/*
 * Full round-trips into Postgres via DAOs, out of Postgres via the NameUsageWrapperMapper, into Elasticsearch via the
 * NameUsageIndexService and finally out of Elasticsearch via the NameUsageSearchService. We have to massage the
 * in-going out-going name usages slightly to allow them to be compared, but not much. (For example the recursive query
 * we execute in Postgres, and the resulting sort order, cannot be emulated with Elasticsearch.)
 */
public class NameUsageIndexServiceIT extends EsReadWriteTestBase {

  @Test
  public void indexDatasetTaxaOnly() throws IOException {
    // Create, insert (into postgres) and return 7 taxa belonging to EsSetupRule.DATASET_KEY
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
  public void createEditorialDecision() throws IOException {
    // Insert 3 taxa into postgres
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(3);
    // Pump them over to Elasticsearch
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    // Make 1st taxon the "subject" of an editorial decision
    Taxon edited = pgTaxa.get(0);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(SimpleName.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    // Save the decision to postgres: triggers sync() on the index service
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc);
    int key = dao.create(decision, edited.getCreatedBy());
    NameSearchResponse res = query(new TermQuery("decisionKey", key));
    assertEquals(1, res.getResult().size());
    assertEquals(edited.getId(), res.getResult().get(0).getUsage().getId());
  }

  @Test
  public void updateEditorialDecision() throws IOException {
    // Insert 3 taxa into postgresindexDatasetTaxaOnly
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(3);
    // Pump them over to Elasticsearch
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    // Make 1st taxon the "subject" of an editorial decision
    Taxon edited = pgTaxa.get(0);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(SimpleName.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    // Save the decision to postgres: triggers sync() on the index service
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc);
    int key = dao.create(decision, edited.getCreatedBy());
    NameSearchResponse res = query(new TermQuery("decisionKey", key));
    assertEquals(pgTaxa.get(0).getId(), res.getResult().get(0).getUsage().getId());
    decision.setKey(key);
    // Change subject of the decision so now 2 taxa should be deleted first and then re-indexed.
    decision.setSubject(SimpleName.of(pgTaxa.get(1)));
    dao.update(decision, edited.getCreatedBy());
    res = query(new TermQuery("decisionKey", key));
    assertEquals(1, res.getResult().size()); // Still only 1 document with this decision key
    assertEquals(pgTaxa.get(1).getId(), res.getResult().get(0).getUsage().getId()); // But it's another document now
  }

  @Test
  public void deleteEditorialDecision() throws IOException {
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(4);
    // Pump them over to Elasticsearch
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    // Make 1st taxon the "subject" of an editorial decision
    Taxon edited = pgTaxa.get(2);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(SimpleName.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    // Save the decision to postgres: triggers sync() on the index service
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc);
    int key = dao.create(decision, edited.getCreatedBy());
    NameSearchResponse res = query(new TermQuery("decisionKey", key));
    assertEquals(pgTaxa.get(2).getId(), res.getResult().get(0).getUsage().getId());
    dao.delete(key, 0);
    res = query(new TermQuery("usageId", pgTaxa.get(2).getId()));
    assertNull(res.getResult().get(0).getDecisionKey());
  }

  @Test
  @Ignore // Just some JSON to send using the REST API
  public void printDecision() throws JsonProcessingException {
    SimpleName sn = new SimpleName("s1", "Larus Fuscus", Rank.SPECIES);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(sn);
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(11);
    decision.setCreatedBy(0);
    decision.setModifiedBy(0);
    System.out.println(EsModule.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(decision));
  }

  private static void massageTaxa(List<Taxon> taxa) {
    // Cannot compare created and modified fields (probably current time when null)
    taxa.forEach(t -> {
      t.setCreated(null);
      t.setModified(null);
      t.getName().setCreated(null);
      t.getName().setModified(null);
    });
    // The order in which taxa flow from Postgres to Elasticsearch is impossible to reproduce with an es query, so just
    // re-order by id
    taxa.sort(Comparator.comparing(Taxon::getId));
  }

}
