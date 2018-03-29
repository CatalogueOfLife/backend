package org.col.db.mapper;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.util.BeanPrinter;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 *
 */
public class NameMapperTest extends org.col.db.mapper.MapperTestBase<NameMapper> {

  private static final Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();
  private NameMapper nameMapper;
  private SynonymMapper synonymMapper;
  private TaxonMapper taxonMapper;
  
  public NameMapperTest() {
    super(NameMapper.class);
  }

  @Before
  public void initMappers() {
    nameMapper = initMybatisRule.getMapper(NameMapper.class);
    synonymMapper = initMybatisRule.getMapper(SynonymMapper.class);
    taxonMapper = initMybatisRule.getMapper(TaxonMapper.class);
  }

  private static Name create(final String id, final Name basionym) throws Exception {
    Name n = TestEntityGenerator.newName(id);
    n.setBasionymKey(basionym.getKey());
    return n;
  }

  private static Name create(Dataset d) throws Exception {
    Name n = TestEntityGenerator.newName();
    n.setDatasetKey(d.getKey());
    return n;
  }

  @Test
  public void roundtrip() throws Exception {
    Name n1 = TestEntityGenerator.newName("sk1");
    nameMapper.create(n1);
    assertNotNull(n1.getKey());
    commit();

    int n1Key = nameMapper.lookupKey(n1.getId(), TestEntityGenerator.DATASET1.getKey());
    assertEquals((Integer) n1Key, n1.getKey());

    Name n1b = nameMapper.get(n1Key);
    assertEquals(n1, n1b);

    // now with basionym
    Name n2 = TestEntityGenerator.newName("sk2");
    n2.setBasionymKey(n1.getKey());
    nameMapper.create(n2);

    commit();

    // we use a new instance of n1 with just the keys for the equality tests
    // n1 = new Name();
    // n1.setKey(n2.getBasionymKey());
    // n1.setId(n2.getBasionymKey());
    // n2.setBasionymKey(n1);

    int n2Key = nameMapper.lookupKey(n2.getId(), TestEntityGenerator.DATASET1.getKey());
    assertEquals((Integer) n2Key, n2.getKey());
    Name n2b = nameMapper.get(n2Key);
    assertEquals(n2, n2b);
  }

  @Test
  public void list() throws Exception {
    List<Name> names = Lists.newArrayList();
    names.add(create(TestEntityGenerator.DATASET2));
    names.add(create(TestEntityGenerator.DATASET2));
    names.add(create(TestEntityGenerator.DATASET2));
    names.add(create(TestEntityGenerator.DATASET2));
    names.add(create(TestEntityGenerator.DATASET2));

    for (Name n : names) {
      nameMapper.create(n);
    }
    commit();

    // get first page
    Page p = new Page(0, 3);

    List<Name> res = nameMapper.list(TestEntityGenerator.DATASET2.getKey(), p);
    assertEquals(3, res.size());
    assertEquals(Lists.partition(names, 3).get(0), res);

    // next page
    p.next();
    res = nameMapper.list(TestEntityGenerator.DATASET2.getKey(), p);
    assertEquals(2, res.size());
    List<Name> l2 = Lists.partition(names, 3).get(1);
    assertEquals(l2, res);
  }

  @Test
  public void count() throws Exception {
    assertEquals(4, nameMapper.count(TestEntityGenerator.DATASET1.getKey()));

    nameMapper.create(TestEntityGenerator.newName());
    nameMapper.create(TestEntityGenerator.newName());
    commit();
    assertEquals(6, nameMapper.count(TestEntityGenerator.DATASET1.getKey()));
  }

  @Test
  public void basionymGroup() throws Exception {
    Name n2bas = TestEntityGenerator.newName("n2");
    nameMapper.create(n2bas);

    Name n1 = create("n1", n2bas);
    nameMapper.create(n1);

    Name n3 = create("n3", n2bas);
    nameMapper.create(n3);

    Name n4 = create("n4", n2bas);
    nameMapper.create(n4);

    commit();

    List<Name> s1 = nameMapper.basionymGroup(n1.getKey());
    assertEquals(4, s1.size());

    List<Name> s2 = nameMapper.basionymGroup(n2bas.getKey());
    assertEquals(4, s2.size());
    assertEquals(s1, s2);

    List<Name> s3 = nameMapper.basionymGroup(n3.getKey());
    assertEquals(4, s3.size());
    assertEquals(s1, s3);

    List<Name> s4 = nameMapper.basionymGroup(n4.getKey());
    assertEquals(4, s4.size());
    assertEquals(s1, s4);
  }

  /*
   * Checks difference in behaviour between providing non-existing key
   * and providing existing key but without synonyms.
   * yields null (issue #55)
   */
  @Test
  public void basionymGroup2() throws Exception {
    Name n = TestEntityGenerator.newName("nxx");
    nameMapper.create(n);
    List<Name> s = nameMapper.basionymGroup(n.getKey());
    assertNotNull("01", s);
    s = nameMapper.basionymGroup(-1);
    assertNotNull("02", s);
    assertEquals("03", 0, s.size());
  }

  @Test
  // Test with rank as extra search criterion
  public void searchWithRank() throws Exception {
    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setRank(Rank.SPECIES);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setRank(Rank.PHYLUM);
    nameMapper.create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    List<NameUsage> names = nameMapper.search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setRank(Rank.SPECIES);
    names = nameMapper.search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = nameMapper.search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setRank(Rank.PHYLUM);
    names = nameMapper.search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setRank(Rank.CLASS);
    names = nameMapper.search(search, new Page());
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
    nameMapper.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setType(NameType.SCIENTIFIC);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setType(NameType.SCIENTIFIC);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setType(NameType.VIRUS);
    nameMapper.create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    List<NameUsage> names = nameMapper.search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setType(NameType.SCIENTIFIC);
    names = nameMapper.search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = nameMapper.search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setType(NameType.VIRUS);
    names = nameMapper.search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setType(NameType.HYBRID_FORMULA);
    names = nameMapper.search(search, new Page());
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
    nameMapper.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setNomStatus(NomStatus.UNEVALUATED);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setNomStatus(NomStatus.UNEVALUATED);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setNomStatus(NomStatus.CHRESONYM);
    nameMapper.create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    List<NameUsage> names = nameMapper.search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setNomStatus(NomStatus.UNEVALUATED);
    names = nameMapper.search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = nameMapper.search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setNomStatus(NomStatus.CHRESONYM);
    names = nameMapper.search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setNomStatus(NomStatus.DOUBTFUL);
    names = nameMapper.search(search, new Page());
    assertEquals("05", 0, names.size());
  }

  @Test
  // Test synonymy
  public void searchWithSynonyms() throws Exception {

    Synonym syn1 = newSynonym("Syn one"); // 1
    Synonym syn2 = newSynonym("Syn two"); // 2

    Name acc1 = newAcceptedName("Accepted one"); // 3
    Name acc2 = newAcceptedName("Accepted two"); // 4
    Name acc3 = newAcceptedName("Accepted three"); // 5

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setAccepted(Arrays.asList(t1, t2));
    syn2.setAccepted(Arrays.asList(t3));

    saveSynonym(syn1);
    saveSynonym(syn2);

    commit();

    // Since we have an empty NameSearch, we should just have all names;
    // the ones created here + the ones inserted through apple
    List<NameUsage> result = nameMapper.search(new NameSearch(), new Page());
    assertEquals(9, result.size());

    assertEquals(2, ((Synonym) result.get(7)).getAccepted().size());

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

    syn1.setAccepted(Arrays.asList(t1, t2));
    syn2.setAccepted(Arrays.asList(t3));

    saveSynonym(syn1);
    saveSynonym(syn2);

    commit();

    NameSearch query = new NameSearch();
    // Provide key of synonym name
    query.setKey(syn1.getName().getKey());
    List<NameUsage> result = nameMapper.search(query, new Page());
    assertEquals(1, result.size());

    assertTrue(result.get(0) instanceof Synonym);
    Synonym sRes = (Synonym) result.get(0);

    assertEquals(2, sRes.getAccepted().size());
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

    syn1.setAccepted(Arrays.asList(t1, t2));
    syn2.setAccepted(Arrays.asList(t3));

    saveSynonym(syn1);
    saveSynonym(syn2);

    commit();

    NameSearch query = new NameSearch();
    // Provide key of accepted name
    query.setKey(acc1.getKey());
    List<NameUsage> result = nameMapper.search(query, new Page());
    assertEquals(1, result.size());

    assertTrue(result.get(0) instanceof Taxon);
    Taxon res = (Taxon) result.get(0);

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

    syn1.setAccepted(Arrays.asList(t1, t2));
    syn2.setAccepted(Arrays.asList(t3));

    saveSynonym(syn1);
    saveSynonym(syn2);

    commit();

    NameSearch query = new NameSearch();
    query.setSortBy(NameSearch.SortBy.RELEVANCE);
    List<NameUsage> result = nameMapper.search(query, new Page());

    query.setSortBy(NameSearch.SortBy.NAME);
    result = nameMapper.search(query, new Page());

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

  private static Taxon newTaxon(String id, Name n) {
    Taxon t = new Taxon();
    t.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    t.setId(id);
    t.setName(n);
    t.setOrigin(Origin.SOURCE);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    return t;
  }

  private void saveSynonym(Synonym syn) throws SQLException {
    for (Taxon t : syn.getAccepted()) {
      if (t.getKey() == null) {
        nameMapper.create(t.getName());
        taxonMapper.create(t);
      }
    }
    nameMapper.create(syn.getName());
    synonymMapper.create(syn);
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
    nameMapper.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setIssues(issue);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setIssues(issue);
    nameMapper.create(n);

    Set<Issue> otherIssue = EnumSet.noneOf(Issue.class);
    otherIssue.add(Issue.BIB_REFERENCE_INVALID);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setIssues(otherIssue);
    nameMapper.create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    List<NameUsage> names = nameMapper.search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setIssue(Issue.UNPARSABLE_AUTHORSHIP);
    names = nameMapper.search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = nameMapper.search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setIssue(Issue.BIB_REFERENCE_INVALID);
    names = nameMapper.search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setIssue(Issue.ALT_IDENTIFIER_INVALID);
    names = nameMapper.search(search, new Page());
    assertEquals("05", 0, names.size());
  }

  @Test
  public void countSearchResults() throws Exception {
    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setRank(Rank.SPECIES);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    nameMapper.create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setRank(Rank.PHYLUM);
    nameMapper.create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    int count = nameMapper.countSearchResults(search);
    assertEquals("01", 3, count);

    search.setRank(Rank.SPECIES);
    count = nameMapper.countSearchResults(search);
    assertEquals("02", 2, count);

    search.setQ("baz");
    count = nameMapper.countSearchResults(search);
    assertEquals("03", 1, count);

    search.setQ("Foo");
    search.setRank(Rank.PHYLUM);
    count = nameMapper.countSearchResults(search);
    assertEquals("04", 1, count);

    search.setRank(Rank.CLASS);
    count = nameMapper.countSearchResults(search);
    assertEquals("05", 0, count);
  }

}
