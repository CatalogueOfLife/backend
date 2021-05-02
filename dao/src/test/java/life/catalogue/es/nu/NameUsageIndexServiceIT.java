package life.catalogue.es.nu;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.EditorialDecision.Mode;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.DecisionDao;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.es.*;
import life.catalogue.es.query.TermQuery;
import life.catalogue.es.query.TermsQuery;
import life.catalogue.matching.NameIndexFactory;
import org.gbif.nameparser.api.Rank;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static life.catalogue.db.PgSetupRule.getSqlSessionFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/*
 * Full round-trips into Postgres via DAOs, out of Postgres via the NameUsageWrapperMapper, into Elasticsearch via the NameUsageIndexService
 * and finally out of Elasticsearch via the NameUsageSearchService. We have to massage the in-going out-going name usages slightly to allow
 * them to be compared, but not much. (For example the recursive query we execute in Postgres, and the resulting sort order, cannot be
 * emulated with Elasticsearch.)
 */
public class NameUsageIndexServiceIT extends EsReadWriteTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceIT.class);

  @Test
  public void indexDatasetTaxaOnly() throws IOException {
    // Create, insert (into postgres) and return 7 taxa belonging to EsSetupRule.DATASET_KEY
    List<Taxon> pgTaxa = createPgTaxa(7);
    createIndexService().indexDataset(EsSetupRule.DATASET_KEY);
    List<String> ids = pgTaxa.stream().map(Taxon::getId).collect(toList());
    NameUsageSearchResponse res = query(new TermsQuery("usageId", ids));
    List<Taxon> esTaxa = res.getResult().stream().map(nuw -> (Taxon) nuw.getUsage()).collect(toList());
    massageTaxa(pgTaxa);
    massageTaxa(esTaxa);
    assertEquals(pgTaxa, esTaxa);
  }

  @Test
  public void indexAll() {
    NameUsageIndexService.Stats stats = createIndexService().indexAll();
    assertEquals(4, stats.usages);
    assertEquals(1, stats.names);
  }

  @Test
  public void createEditorialDecision() {
    // Insert 3 taxa into postgres
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(3);
    // Pump them over to Elasticsearch
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    // Make 1st taxon the "subject" of an editorial decision
    Taxon edited = pgTaxa.get(0);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(SimpleNameLink.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(Datasets.COL);
    decision.setSubjectDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    // Save the decision to postgres: triggers sync() on the index service
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc);
    dao.create(decision, 0);

    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    request.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, Datasets.COL);
    NameUsageSearchResponse res = search(request);

    assertEquals(1, res.getResult().size());
    assertEquals(edited.getId(), res.getResult().get(0).getUsage().getId());
  }

  @Test
  @Ignore
  public void issue407() throws IOException {

    int USER_ID = 10;
    int DATASET_KEY = 11;

    // Extract a taxon from the JSON pasted by thomas into #407. That JSON doesn't have a JSON key (that was the issue), but
    // that suits us fine now.
    InputStream is = getClass().getResourceAsStream("/elastic/Issue407_document.json");
    EsNameUsage doc = EsModule.readDocument(is);
    NameUsageWrapper nuw = NameUsageWrapperConverter.inflate(doc.getPayload());
    NameUsageWrapperConverter.enrichPayload(nuw, doc);
    Taxon taxon = (Taxon) nuw.getUsage();

    // Insert that taxon into Postgres
    NameDao ndao = new NameDao(getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru());
    DSID<String> dsid = ndao.create(taxon.getName(), USER_ID);
    LOG.info(">>>>>>> Name inserted into database. ID: {}\n", dsid.getId());
    TaxonDao tdao = new TaxonDao(getSqlSessionFactory(), ndao, NameUsageIndexService.passThru());
    dsid = tdao.create(taxon, USER_ID);
    LOG.info(">>>>>>> Taxon inserted into database. ID: {}\n", EsModule.writeDebug(taxon));

    // Index the dataset containing the taxon
    NameUsageIndexService svc = createIndexService();
    svc.indexDataset(DATASET_KEY);

    // make sure the decision is empty
    NameUsageSearchResponse res = query(new TermQuery("usageId", dsid.getId())); // Query ES for the usage
    assertEquals(1, res.getResult().size()); // Yes, it's there!
    assertNull(res.getResult().get(0).getDecisions()); // and no decision key yet

    // Now create the decision
    is = getClass().getResourceAsStream("/elastic/Issue407_decision.json");
    EditorialDecision decision = ApiModule.MAPPER.readValue(is, EditorialDecision.class);

    // the taxon has been assigned a new id, use it for the decision
    decision.getSubject().setId(taxon.getId());

    DecisionDao ddao = new DecisionDao(getSqlSessionFactory(), svc);
    int key = ddao.create(decision, USER_ID).getId();
    LOG.info(">>>>>>> Decision inserted into database: {}\n", EsModule.writeDebug(decision));

    res = query(new TermQuery("decisionKey", key)); // Query ES for the decision key
    assertEquals(1, res.getResult().size()); // Yes, it's there!
    assertEquals(taxon.getId(), res.getResult().get(0).getUsage().getId()); // And it belongs to the taxon we just inserted

    res = query(new TermQuery("usageId", dsid.getId())); // Query ES for the usage
    assertEquals(1, res.getResult().size()); // Yes, it's there!
    assertEquals(key, (int) res.getResult().get(0).getDecisions().get(0).getId()); // make sure it has the decision key
  }

  @Test
  public void updateEditorialDecision() {
    // Insert 3 taxa into postgresindexDatasetTaxaOnly
    NameUsageIndexService svc = createIndexService();
    List<Taxon> pgTaxa = createPgTaxa(3);
    // Pump them over to Elasticsearch
    svc.indexDataset(EsSetupRule.DATASET_KEY);
    // Make 1st taxon the "subject" of an editorial decision
    Taxon edited = pgTaxa.get(0);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(SimpleNameLink.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(Datasets.COL);
    decision.setSubjectDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    // Save the decision to postgres: triggers sync() on the index service
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc);
    int key = dao.create(decision, edited.getCreatedBy()).getId();

    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    request.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, Datasets.COL);
    NameUsageSearchResponse res = search(request);

    assertEquals(pgTaxa.get(0).getId(), res.getResult().get(0).getUsage().getId());
    decision.setId(key);
    // Change subject of the decision so now 2 taxa should be deleted first and then re-indexed.
    decision.setSubject(SimpleNameLink.of(pgTaxa.get(1)));
    dao.update(decision, edited.getCreatedBy());

    res = search(request);

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
    decision.setSubject(SimpleNameLink.of(edited));
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(Datasets.COL);
    decision.setSubjectDatasetKey(edited.getDatasetKey());
    decision.setCreatedBy(edited.getCreatedBy());
    decision.setModifiedBy(edited.getCreatedBy());
    // Save the decision to postgres: triggers sync() on the index service
    DecisionDao dao = new DecisionDao(getSqlSessionFactory(), svc);
    DSID<Integer> key = dao.create(decision, edited.getCreatedBy());

    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    request.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, Datasets.COL);
    NameUsageSearchResponse res = search(request);

    assertEquals(pgTaxa.get(2).getId(), res.getResult().get(0).getUsage().getId());
    dao.delete(key, 0);
    res = query(new TermQuery("usageId", pgTaxa.get(2).getId()));
    assertNull(res.getResult().get(0).getDecisions());
  }

  // Some JSON to send using the REST API
  void printDecision() {
    SimpleNameLink sn = SimpleNameLink.of("s1", "Larus Fuscus", Rank.SPECIES);
    EditorialDecision decision = new EditorialDecision();
    decision.setSubject(sn);
    decision.setMode(Mode.UPDATE);
    decision.setDatasetKey(11);
    decision.setCreatedBy(0);
    decision.setModifiedBy(0);
    System.out.println(EsModule.writeDebug(decision));
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
