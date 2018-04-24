package org.col.db.dao;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.ibatis.session.SqlSession;
import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.mapper.SynonymMapper;
import org.col.util.BeanPrinter;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;

public class NameUsageDaoTest extends DaoTestBase {
  static final Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();

  NameUsageDao dao;
  NameDao nDao;
  TaxonDao tDao;
  SynonymMapper synonymMapper;
  SqlSession session;

  @Before
  public void init(){
    session = session();
    dao = new NameUsageDao(session);
    nDao = new NameDao(session);
    tDao = new TaxonDao(session);
    synonymMapper = session.getMapper(SynonymMapper.class);
  }

  @After
  public void down(){
    session.close();
  }


  @Test
  // Test with rank as extra search criterion
  public void searchWithRank() throws Exception {
    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    nDao.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setRank(Rank.SPECIES);
    nDao.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    nDao.create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setRank(Rank.PHYLUM);
    nDao.create(n);

    session.commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    ResultPage<NameUsage> names = dao.search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setRank(Rank.SPECIES);
    names = dao.search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = dao.search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setRank(Rank.PHYLUM);
    names = dao.search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setRank(Rank.CLASS);
    names = dao.search(search, new Page());
    assertEquals("05", 0, names.size());
  }

  @Test
  // Test with name type as extra search criterion
  public void searchWithType() throws Exception {
    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setType(NameType.SCIENTIFIC);
    nDao.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setType(NameType.SCIENTIFIC);
    nDao.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setType(NameType.SCIENTIFIC);
    nDao.create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setType(NameType.VIRUS);
    nDao.create(n);

    session.commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    ResultPage<NameUsage> names = dao.search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setType(NameType.SCIENTIFIC);
    names = dao.search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = dao.search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setType(NameType.VIRUS);
    names = dao.search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setType(NameType.HYBRID_FORMULA);
    names = dao.search(search, new Page());
    assertEquals("05", 0, names.size());
  }

  @Test
  // Test with status as extra search criterion
  public void searchWithTaxstatus() throws Exception {
    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setNomStatus(NomStatus.UNEVALUATED);
    nDao.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setNomStatus(NomStatus.UNEVALUATED);
    nDao.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setNomStatus(NomStatus.UNEVALUATED);
    nDao.create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setNomStatus(NomStatus.CHRESONYM);
    nDao.create(n);

    session.commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    ResultPage<NameUsage> names = dao.search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setNomStatus(NomStatus.UNEVALUATED);
    names = dao.search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = dao.search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setNomStatus(NomStatus.CHRESONYM);
    names = dao.search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setNomStatus(NomStatus.DOUBTFUL);
    names = dao.search(search, new Page());
    assertEquals("05", 0, names.size());
  }

  @Test
  // Test synonymy
  public void searchWithSynonyms() throws Exception {
    Synonym syn1 = newSynonym("Syn one");
    Synonym syn2 = newSynonym("Syn two");

    Name acc1 = newAcceptedName("Accepted one");
    Name acc2 = newAcceptedName("Accepted two");
    Name acc3 = newAcceptedName("Accepted three");
    Name n4 = newAcceptedName("Name four");

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setAccepted(t1);
    syn2.setAccepted(t3);
    saveTaxon(t2);
    nDao.create(n4);

    saveSynonym(syn1);
    saveSynonym(syn2);

    session.commit();

    Map<Integer, NameUsage> usages = Maps.newHashMap();
    usages.put(TestEntityGenerator.TAXON1.getName().getKey(), TestEntityGenerator.TAXON1);
    usages.put(TestEntityGenerator.TAXON2.getName().getKey(), TestEntityGenerator.TAXON2);
    usages.put(TestEntityGenerator.SYN1.getName().getKey(), TestEntityGenerator.SYN1);
    usages.put(TestEntityGenerator.SYN2.getName().getKey(), TestEntityGenerator.SYN2);
    usages.put(t1.getName().getKey(), t1);
    usages.put(t2.getName().getKey(), t2);
    usages.put(t3.getName().getKey(), t3);
    usages.put(syn1.getName().getKey(), syn1);
    usages.put(syn2.getName().getKey(), syn2);
    usages.put(n4.getKey(), new BareName(n4));

    // Since we have an empty NameSearch, we should just have all names;
    // the ones created here + the ones inserted through apple
    ResultPage<NameUsage> result = dao.search(new NameSearch(), new Page());
    assertEquals(10, result.size());

    for (NameUsage u : result.getResult()) {
      NameUsage u2 = usages.get(u.getName().getKey());
      assertEquals(u2.getName().getKey(), u.getName().getKey());
      assertEquals(u2.getClass(), u.getClass());
      //assertEquals(u2, u);
    }
  }

  @Test
  // Test synonymy (make sure "accepted" property will be set correctly)
  public void searchWithSynonyms2() throws Exception {
    Synonym syn1 = newSynonym("Syn one"); // 1
    Synonym syn2 = newSynonym("Syn two"); // 2

    Name acc1 = newAcceptedName("Accepted one"); // 3
    Name acc2 = newAcceptedName("Accepted two"); // 4
    Name acc3 = newAcceptedName("Accepted three"); // 5

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setAccepted(t1);
    syn2.setAccepted(t3);

    saveSynonym(syn1);
    saveSynonym(syn2);
    saveTaxon(t2);

    session.commit();

    NameSearch query = new NameSearch();
    // Provide key of synonym name
    query.setKey(syn1.getName().getKey());
    ResultPage<NameUsage> result = dao.search(query, new Page());
    assertEquals(1, result.size());

    assertTrue(result.getResult().get(0) instanceof Synonym);
    Synonym sRes = (Synonym) result.getResult().get(0);

    assertEquals(t1, sRes.getAccepted());
    BeanPrinter bp = new BeanPrinter();
    bp.setShowObjectIds(true);
  }

  @Test
  // Test synonymy (make sure "accepted" property will be set correctly)
  public void searchWithSynonyms3() throws Exception {
    Synonym syn1 = newSynonym("Syn one"); // 1
    Synonym syn2 = newSynonym("Syn two"); // 2

    Name acc1 = newAcceptedName("Accepted one"); // 3
    Name acc2 = newAcceptedName("Accepted two"); // 4
    Name acc3 = newAcceptedName("Accepted three"); // 5

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setAccepted(t1);
    syn2.setAccepted(t3);
    saveTaxon(t2);

    saveSynonym(syn1);
    saveSynonym(syn2);

    session.commit();

    NameSearch query = new NameSearch();
    // Provide key of accepted name
    query.setKey(acc1.getKey());
    ResultPage<NameUsage> result = dao.search(query, new Page());
    assertEquals(1, result.size());

    assertTrue(result.getResult().get(0) instanceof Taxon);
    Taxon res = (Taxon) result.getResult().get(0);

    assertNotNull(res);
    assertNotNull(res.getKey());
  }

  @Test
  // Test sorting (only make sure we generate valid SQL)
  public void searchSort() throws Exception {
    Synonym syn1 = newSynonym("Syn one"); // 1
    Synonym syn2 = newSynonym("Syn two"); // 2

    Name acc1 = newAcceptedName("Accepted one"); // 3
    Name acc2 = newAcceptedName("Accepted two"); // 4
    Name acc3 = newAcceptedName("Accepted three"); // 5

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setAccepted(t1);
    syn2.setAccepted(t3);
    saveTaxon(t2);

    saveSynonym(syn1);
    saveSynonym(syn2);

    session.commit();

    NameSearch query = new NameSearch();
    query.setSortBy(NameSearch.SortBy.RELEVANCE);
    ResultPage<NameUsage> result = dao.search(query, new Page());

    query.setSortBy(NameSearch.SortBy.NAME);
    result = dao.search(query, new Page());
  }

  @Test
  // Test with issue as extra search criterion
  public void searchWithIssue() throws Exception {
    Set<Issue> issue = EnumSet.noneOf(Issue.class);
    issue.add(Issue.UNPARSABLE_AUTHORSHIP);

    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setIssues(issue);
    nDao.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setIssues(issue);
    nDao.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setIssues(issue);
    nDao.create(n);

    Set<Issue> otherIssue = EnumSet.noneOf(Issue.class);
    otherIssue.add(Issue.REFERENCE_UNPARSABLE);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setIssues(otherIssue);
    nDao.create(n);

    session.commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    ResultPage<NameUsage> names = dao.search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setIssue(Issue.UNPARSABLE_AUTHORSHIP);
    names = dao.search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = dao.search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setIssue(Issue.REFERENCE_UNPARSABLE);
    names = dao.search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setIssue(Issue.ALT_IDENTIFIER_INVALID);
    names = dao.search(search, new Page());
    assertEquals("05", 0, names.size());
  }


  private static Taxon newTaxon(String id, Name n) {
    Taxon t = new Taxon();
    t.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    t.setId(id);
    t.setName(n);
    t.setOrigin(Origin.SOURCE);
    return t;
  }

  private void saveTaxon(Taxon t) throws SQLException {
    if (t.getKey() == null) {
      nDao.create(t.getName());
      tDao.create(t);
    }
  }

  private void saveSynonym(Synonym syn) throws SQLException {
    saveTaxon(syn.getAccepted());
    nDao.create(syn.getName());
    synonymMapper.create(syn);
  }

  private static Synonym newSynonym(String scientificName) {
    Name n = new Name();
    n.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    n.setId(scientificName.toLowerCase().replace(' ', '-'));
    n.setScientificName(scientificName);

    List<String> tokens = SPACE_SPLITTER.splitToList(scientificName);
    n.setGenus(tokens.get(0));
    n.setSpecificEpithet(tokens.get(1));
    n.setOrigin(Origin.SOURCE);
    n.setType(NameType.SCIENTIFIC);

    Synonym syn = new Synonym();
    syn.setName(n);
    syn.setStatus(TaxonomicStatus.SYNONYM);
    return syn;
  }

  private static Name newAcceptedName(String scientificName) {
    Name name = new Name();
    name.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    name.setId(scientificName.toLowerCase().replace(' ', '-'));
    name.setScientificName(scientificName);
    List<String> tokens = SPACE_SPLITTER.splitToList(scientificName);
    name.setGenus(tokens.get(0));
    name.setSpecificEpithet(tokens.get(1));
    name.setOrigin(Origin.SOURCE);
    name.setType(NameType.SCIENTIFIC);
    return name;
  }
}
