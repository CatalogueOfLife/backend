package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Identifier;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.search.VernacularSearchRequest;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.TermFactory;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.NAME1;
import static life.catalogue.api.TestEntityGenerator.TAXON1;
import static life.catalogue.db.mapper.LogicalOperator.AND;
import static life.catalogue.db.mapper.LogicalOperator.OR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Free text coming from the API must only ever reach the SQL as a bound parameter.
 * Every method below hands a value to a mapper that would end the string literal if it were
 * interpolated into the statement: a single quote, a backslash and the two dollar quote tags the
 * mappers used to wrap values in. The query must execute and simply find nothing.
 */
public class SqlInjectionMapperTest extends MapperTestBase<DatasetMapper> {
  static final String HOSTILE = "x' \\ $Grtz5$ $gbif$ --";

  public SqlInjectionMapperTest() {
    super(DatasetMapper.class);
  }

  @Test
  public void datasetSearch() {
    DatasetSearchRequest req = new DatasetSearchRequest();
    req.setQ(HOSTILE);
    assertTrue(mapper().search(req, null, new Page()).isEmpty());
    assertTrue(mapper().searchKeys(req, null).isEmpty());
    assertEquals(0, mapper().count(req, null));
  }

  /**
   * A row type that is not a known term is kept as an unknown term with its full URI,
   * so a quote in the URI must not reach the SQL either.
   */
  @Test
  public void datasetRowType() {
    DatasetSearchRequest req = new DatasetSearchRequest();
    req.setRowType(List.of(TermFactory.instance().findTerm("http://example.org/terms/x'y")));
    assertTrue(mapper().search(req, null, new Page()).isEmpty());
    assertEquals(0, mapper().count(req, null));
  }

  @Test
  public void datasetSuggest() {
    assertTrue(mapper().suggest(HOSTILE, null, true, 10).isEmpty());
  }

  @Test
  public void publisherSearch() {
    assertTrue(mapper(PublisherMapper.class).search(HOSTILE, new Page()).isEmpty());
  }

  @Test
  public void vernacularSearch() {
    var vm = mapper(VernacularNameMapper.class);
    VernacularSearchRequest req = new VernacularSearchRequest();
    req.setQ(HOSTILE);
    assertTrue(vm.search(appleKey, req, new Page()).isEmpty());
    assertEquals(0, vm.count(appleKey, req));
    assertTrue(vm.searchAll(HOSTILE, null, new Page()).isEmpty());
  }

  @Test
  public void sectorSearch() {
    SectorSearchRequest req = new SectorSearchRequest();
    req.setName(HOSTILE);
    var sm = mapper(SectorMapper.class);
    assertTrue(sm.search(req, new Page()).isEmpty());
    assertEquals(0, sm.countSearch(req));
    assertTrue(sm.duplicates(req, new Page()).isEmpty());
  }

  @Test
  public void decisionSearch() {
    DecisionSearchRequest req = new DecisionSearchRequest();
    req.setName(HOSTILE);
    var dm = mapper(DecisionMapper.class);
    assertTrue(dm.search(req, new Page()).isEmpty());
    assertEquals(0, dm.countSearch(req));
  }

  @Test
  public void estimateSearch() {
    EstimateSearchRequest req = new EstimateSearchRequest();
    req.setName(HOSTILE);
    var em = mapper(EstimateMapper.class);
    assertTrue(em.search(req, new Page()).isEmpty());
    assertEquals(0, em.countSearch(req));
  }

  @Test
  public void namePrefix() {
    NameMapper.NameSearchRequest req = new NameMapper.NameSearchRequest();
    req.namePrefix = HOSTILE;
    assertTrue(mapper(NameMapper.class).search(appleKey, req, new Page()).isEmpty());
  }

  @Test
  public void nameUsageRegex() {
    assertTrue(mapper(NameUsageMapper.class).listByRegex(appleKey, null, HOSTILE, null, null, null, null, new Page()).isEmpty());
  }

  @Test
  public void namesIndexRegex() {
    assertTrue(mapper(NamesIndexMapper.class).listByRegex(HOSTILE, new Page()).isEmpty());
  }

  @Test
  public void verbatimTermValues() {
    var vm = mapper(VerbatimRecordMapper.class);
    Map<org.gbif.dwc.terms.Term, String> values = Map.of(DwcTerm.genus, HOSTILE, DwcTerm.family, HOSTILE);
    assertTrue(vm.list(appleKey, null, values, AND, null, null, null, new Page()).isEmpty());
    assertTrue(vm.list(appleKey, null, values, OR, null, null, null, new Page()).isEmpty());
    assertEquals(0, vm.count(appleKey, null, values, AND, null, null, null));
    assertEquals(0, vm.count(appleKey, null, values, OR, null, null, null));
  }

  @Test
  public void userSearch() {
    var um = mapper(UserMapper.class);
    assertTrue(um.search(HOSTILE, null, new Page()).isEmpty());
    assertEquals(0, um.searchCount(HOSTILE, null));
  }

  /**
   * Several identifiers at once must all arrive, each bound on its own: a bind inside the foreach would hand every
   * placeholder the last value, so the hostile one and a plain one are added together.
   */
  @Test
  public void nameIdentifiers() {
    var nm = mapper(NameMapper.class);
    var key = DSID.of(appleKey, NAME1.getId());
    var hostile = new Identifier("local", HOSTILE);
    var tsn = new Identifier("tsn", "12345");
    nm.addIdentifier(key, List.of(hostile, tsn));
    var stored = nm.get(key).getIdentifier();
    assertTrue(stored.contains(hostile));
    assertTrue(stored.contains(tsn));
  }

  @Test
  public void usageIdentifiers() {
    var um = mapper(NameUsageMapper.class);
    var key = DSID.of(appleKey, TAXON1.getId());
    var hostile = new Identifier("local", HOSTILE);
    var tsn = new Identifier("tsn", "12345");
    um.addIdentifier(key, List.of(hostile, tsn));
    var stored = um.get(key).getIdentifier();
    assertTrue(stored.contains(hostile));
    assertTrue(stored.contains(tsn));
  }
}
