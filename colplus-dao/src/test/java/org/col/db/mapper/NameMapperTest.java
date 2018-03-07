package org.col.db.mapper;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.api.TestEntityGenerator;
import org.col.db.mapper.temp.NameSearchResultTemp;
import org.col.util.BeanPrinter;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
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

  public NameMapperTest() {
    super(NameMapper.class);
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
    mapper().create(n1);
    assertNotNull(n1.getKey());
    commit();

    int n1Key = mapper().lookupKey(n1.getId(), TestEntityGenerator.DATASET1.getKey());
    assertEquals((Integer) n1Key, n1.getKey());

    Name n1b = mapper().get(n1Key);
    assertEquals(n1, n1b);

    // now with basionym
    Name n2 = TestEntityGenerator.newName("sk2");
    n2.setBasionymKey(n1.getKey());
    mapper().create(n2);

    commit();

    // we use a new instance of n1 with just the keys for the equality tests
    // n1 = new Name();
    // n1.setKey(n2.getBasionymKey());
    // n1.setId(n2.getBasionymKey());
    // n2.setBasionymKey(n1);

    int n2Key = mapper().lookupKey(n2.getId(), TestEntityGenerator.DATASET1.getKey());
    assertEquals((Integer) n2Key, n2.getKey());
    Name n2b = mapper().get(n2Key);
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
      mapper().create(n);
    }
    commit();

    // get first page
    Page p = new Page(0, 3);

    List<Name> res = mapper().list(TestEntityGenerator.DATASET2.getKey(), p);
    assertEquals(3, res.size());
    assertEquals(Lists.partition(names, 3).get(0), res);

    // next page
    p.next();
    res = mapper().list(TestEntityGenerator.DATASET2.getKey(), p);
    assertEquals(2, res.size());
    List<Name> l2 = Lists.partition(names, 3).get(1);
    assertEquals(l2, res);
  }

  @Test
  public void count() throws Exception {
    assertEquals(4, mapper().count(TestEntityGenerator.DATASET1.getKey()));

    mapper().create(TestEntityGenerator.newName());
    mapper().create(TestEntityGenerator.newName());
    commit();
    assertEquals(6, mapper().count(TestEntityGenerator.DATASET1.getKey()));
  }

  @Test
  public void basionymGroup() throws Exception {
    Name n2bas = TestEntityGenerator.newName("n2");
    mapper().create(n2bas);

    Name n1 = create("n1", n2bas);
    mapper().create(n1);

    Name n3 = create("n3", n2bas);
    mapper().create(n3);

    Name n4 = create("n4", n2bas);
    mapper().create(n4);

    commit();

    List<Name> s1 = mapper().basionymGroup(n1.getKey());
    assertEquals(4, s1.size());

    List<Name> s2 = mapper().basionymGroup(n2bas.getKey());
    assertEquals(4, s2.size());
    assertEquals(s1, s2);

    List<Name> s3 = mapper().basionymGroup(n3.getKey());
    assertEquals(4, s3.size());
    assertEquals(s1, s3);

    List<Name> s4 = mapper().basionymGroup(n4.getKey());
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
    mapper().create(n);
    List<Name> s = mapper().basionymGroup(n.getKey());
    assertNotNull("01", s);
    s = mapper().basionymGroup(-1);
    assertNotNull("02", s);
    assertEquals("03", 0, s.size());
  }

  @Test
  public void synonyms() throws Exception {
    final int accKey = TestEntityGenerator.TAXON1.getKey();
    final int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();

    List<Name> synonyms = mapper().synonyms(accKey);
    assertTrue(synonyms.isEmpty());
    assertEquals(0, synonyms.size());

    // homotypic 1
    Name syn1 = TestEntityGenerator.newName("syn1");
    mapper().create(syn1);

    // homotypic 2
    Name syn2bas = TestEntityGenerator.newName("syn2bas");
    mapper().create(syn2bas);

    Name syn21 = TestEntityGenerator.newName("syn2.1");
    syn21.setBasionymKey(syn2bas.getKey());
    mapper().create(syn21);

    Name syn22 = TestEntityGenerator.newName("syn2.2");
    syn22.setBasionymKey(syn2bas.getKey());
    mapper().create(syn22);

    // homotypic 3
    Name syn3bas = TestEntityGenerator.newName("syn3bas");
    mapper().create(syn3bas);

    Name syn31 = TestEntityGenerator.newName("syn3.1");
    syn31.setBasionymKey(syn3bas.getKey());
    mapper().create(syn31);

    commit();

    // no synonym links added yet, expect empty synonymy even though basionym links
    // exist!
    synonyms = mapper().synonyms(accKey);
    assertTrue(synonyms.isEmpty());
    assertEquals(0, synonyms.size());

    // now add a few synonyms
    mapper().addSynonym(datasetKey, accKey, syn1.getKey());
    commit();
    synonyms = mapper().synonyms(accKey);
    assertFalse(synonyms.isEmpty());
    assertEquals(1, synonyms.size());

    mapper().addSynonym(datasetKey, accKey, syn2bas.getKey());
    mapper().addSynonym(datasetKey, accKey, syn21.getKey());
    mapper().addSynonym(datasetKey, accKey, syn22.getKey());
    mapper().addSynonym(datasetKey, accKey, syn3bas.getKey());
    mapper().addSynonym(datasetKey, accKey, syn31.getKey());

    synonyms = mapper().synonyms(accKey);
    assertEquals(6, synonyms.size());
  }

  @Test
  // Test with rank as extra search criterion
  public void searchWithRank() throws Exception {
    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    mapper().create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setRank(Rank.SPECIES);
    mapper().create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    mapper().create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setRank(Rank.PHYLUM);
    mapper().create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    List<NameSearchResultTemp> names = mapper().search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setRank(Rank.SPECIES);
    names = mapper().search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = mapper().search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setRank(Rank.PHYLUM);
    names = mapper().search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setRank(Rank.CLASS);
    names = mapper().search(search, new Page());
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
    mapper().create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setType(NameType.SCIENTIFIC);
    mapper().create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setType(NameType.SCIENTIFIC);
    mapper().create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setType(NameType.VIRUS);
    mapper().create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    List<NameSearchResultTemp> names = mapper().search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setType(NameType.SCIENTIFIC);
    names = mapper().search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = mapper().search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setType(NameType.VIRUS);
    names = mapper().search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setType(NameType.HYBRID_FORMULA);
    names = mapper().search(search, new Page());
    assertEquals("05", 0, names.size());
  }

  @Test
  // Test with status as extra search criterion
  public void searchWithTaxstatus() throws Exception {
    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setStatus(NomStatus.UNEVALUATED);
    mapper().create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setStatus(NomStatus.UNEVALUATED);
    mapper().create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setStatus(NomStatus.UNEVALUATED);
    mapper().create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setStatus(NomStatus.CHRESONYM);
    mapper().create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    List<NameSearchResultTemp> names = mapper().search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setNomstatus(NomStatus.UNEVALUATED);
    names = mapper().search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = mapper().search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setNomstatus(NomStatus.CHRESONYM);
    names = mapper().search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setNomstatus(NomStatus.DOUBTFUL);
    names = mapper().search(search, new Page());
    assertEquals("05", 0, names.size());
  }

  @Test
  // Test synonymy
  public void searchWithSynonyms() throws Exception {

    NameSearchResult syn1 = newSynonym("Syn one"); // 1
    NameSearchResult syn2 = newSynonym("Syn two"); // 2

    Name acc1 = newAcceptedName("Accepted one"); // 3
    Name acc2 = newAcceptedName("Accepted two"); // 4
    Name acc3 = newAcceptedName("Accepted three"); // 5

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setTaxa(Arrays.asList(t1, t2));
    syn2.setTaxa(Arrays.asList(t3));

    saveSynonym(syn1);
    saveSynonym(syn2);

    commit();

    // Since we have an empty NameSearch, we should just have all names;
    // the ones created here + the ones inserted through apple
    List<NameSearchResultTemp> result = mapper().search(new NameSearch(), new Page());
    assertEquals(9, result.size());

  }

  @Test
  // Test synonymy (make sure "accepted" property will be set correctly)
  public void searchWithSynonyms2() throws Exception {

    NameSearchResult syn1 = newSynonym("Syn one"); // 1
    NameSearchResult syn2 = newSynonym("Syn two"); // 2

    Name acc1 = newAcceptedName("Accepted one"); // 3
    Name acc2 = newAcceptedName("Accepted two"); // 4
    Name acc3 = newAcceptedName("Accepted three"); // 5

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setTaxa(Arrays.asList(t1, t2));
    syn2.setTaxa(Arrays.asList(t3));

    saveSynonym(syn1);
    saveSynonym(syn2);

    commit();

    NameSearch query = new NameSearch();
    // Provide key of synonym
    query.setKey(syn1.getKey());
    List<NameSearchResultTemp> result = mapper().search(query, new Page());
    assertEquals("01", 1, result.size());
    assertNull("02", result.get(0).getTaxonOfThisName());
    assertNotNull("03", result.get(0).getTaxaOfAcceptedNames());
    BeanPrinter bp = new BeanPrinter();
    bp.setShowObjectIds(true);
  }

  @Test
  // Test synonymy (make sure "accepted" property will be set correctly)
  public void searchWithSynonyms3() throws Exception {

    NameSearchResult syn1 = newSynonym("Syn one"); // 1
    NameSearchResult syn2 = newSynonym("Syn two"); // 2

    Name acc1 = newAcceptedName("Accepted one"); // 3
    Name acc2 = newAcceptedName("Accepted two"); // 4
    Name acc3 = newAcceptedName("Accepted three"); // 5

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setTaxa(Arrays.asList(t1, t2));
    syn2.setTaxa(Arrays.asList(t3));

    saveSynonym(syn1);
    saveSynonym(syn2);

    commit();

    NameSearch query = new NameSearch();
    // Provide key of accepted name
    query.setKey(acc1.getKey());
    List<NameSearchResultTemp> result = mapper().search(query, new Page());
    assertEquals("01", 1, result.size());
    assertNotNull("02", result.get(0).getTaxonOfThisName());
    assertEquals("03", 0, result.get(0).getTaxaOfAcceptedNames().size());
  }

  @Test
  // Test sorting (only make sure we generate valid SQL)
  public void searchSort() throws Exception {

    NameSearchResult syn1 = newSynonym("Syn one"); // 1
    NameSearchResult syn2 = newSynonym("Syn two"); // 2

    Name acc1 = newAcceptedName("Accepted one"); // 3
    Name acc2 = newAcceptedName("Accepted two"); // 4
    Name acc3 = newAcceptedName("Accepted three"); // 5

    Taxon t1 = newTaxon("t-01", acc1);
    Taxon t2 = newTaxon("t-02", acc2);
    Taxon t3 = newTaxon("t-03", acc3);

    syn1.setTaxa(Arrays.asList(t1, t2));
    syn2.setTaxa(Arrays.asList(t3));

    saveSynonym(syn1);
    saveSynonym(syn2);

    commit();

    NameSearch query = new NameSearch();
    query.setSortBy(NameSearch.SortBy.RELEVANCE);
    mapper().search(query, new Page());
    query.setSortBy(NameSearch.SortBy.NAME);
    mapper().search(query, new Page());

  }

  private static NameSearchResult newSynonym(String scientificName) {
    NameSearchResult syn = new NameSearchResult();
    syn.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    syn.setId(scientificName.toLowerCase().replace(' ', '-'));
    syn.setScientificName(scientificName);
    List<String> tokens = SPACE_SPLITTER.splitToList(scientificName);
    syn.setGenus(tokens.get(0));
    syn.setSpecificEpithet(tokens.get(1));
    syn.setOrigin(Origin.SOURCE);
    syn.setType(NameType.SCIENTIFIC);
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

  private void saveSynonym(NameSearchResult syn) throws SQLException {
    mapper().create(syn);
    TaxonMapper taxonMapper = initMybatisRule.getMapper(TaxonMapper.class);
    Connection conn = initMybatisRule.getSqlSession().getConnection();
    String sql = "INSERT INTO synonym(taxon_key,name_key,dataset_key) VALUES (?,?,?)";
    PreparedStatement ps = conn.prepareStatement(sql);
    for (Taxon t : syn.getTaxa()) {
      mapper().create(t.getName());
      taxonMapper.create(t);
      ps.setInt(1, t.getKey());
      ps.setInt(2, syn.getKey());
      ps.setInt(3, syn.getDatasetKey());
      ps.executeUpdate();
    }
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
    mapper().create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setIssues(issue);
    mapper().create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setIssues(issue);
    mapper().create(n);

    Set<Issue> otherIssue = EnumSet.noneOf(Issue.class);
    otherIssue.add(Issue.BIB_REFERENCE_INVALID);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setIssues(otherIssue);
    mapper().create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    List<NameSearchResultTemp> names = mapper().search(search, new Page());
    assertEquals("01", 3, names.size());

    search.setIssue(Issue.UNPARSABLE_AUTHORSHIP);
    names = mapper().search(search, new Page());
    assertEquals("02", 2, names.size());

    search.setQ("baz");
    names = mapper().search(search, new Page());
    assertEquals("03", 1, names.size());

    search.setQ("Foo");
    search.setIssue(Issue.BIB_REFERENCE_INVALID);
    names = mapper().search(search, new Page());
    assertEquals("04", 1, names.size());

    search.setIssue(Issue.ALT_IDENTIFIER_INVALID);
    names = mapper().search(search, new Page());
    assertEquals("05", 0, names.size());
  }

  @Test
  public void countSearchResults() throws Exception {
    Name n = TestEntityGenerator.newName("a");
    n.setScientificName("Foo bar");
    n.setGenus("Foo");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    mapper().create(n);

    n = TestEntityGenerator.newName("b");
    n.setScientificName("Foo baz");
    n.setGenus("Foo");
    n.setSpecificEpithet("baz");
    n.setRank(Rank.SPECIES);
    mapper().create(n);

    n = TestEntityGenerator.newName("c");
    n.setScientificName("Fee bar");
    n.setGenus("Fee");
    n.setSpecificEpithet("bar");
    n.setRank(Rank.SPECIES);
    mapper().create(n);

    n = TestEntityGenerator.newName("d");
    n.setScientificName("Foo");
    n.setRank(Rank.PHYLUM);
    mapper().create(n);

    commit();

    NameSearch search = new NameSearch();
    search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    search.setQ("foo");
    int count = mapper().countSearchResults(search);
    assertEquals("01", 3, count);

    search.setRank(Rank.SPECIES);
    count = mapper().countSearchResults(search);
    assertEquals("02", 2, count);

    search.setQ("baz");
    count = mapper().countSearchResults(search);
    assertEquals("03", 1, count);

    search.setQ("Foo");
    search.setRank(Rank.PHYLUM);
    count = mapper().countSearchResults(search);
    assertEquals("04", 1, count);

    search.setRank(Rank.CLASS);
    count = mapper().countSearchResults(search);
    assertEquals("05", 0, count);
  }

}
