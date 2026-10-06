package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Environment;
import life.catalogue.api.vocab.IgnoreReason;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.release.UsageIdGen;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TreeBaseHandlerTest {

  SqlSessionFactory factory;
  VerbatimRecordMapper vrm;

  @Before
  public void setup() throws Exception {
    TaxonMapper tm = mock(TaxonMapper.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    VerbatimSourceMapper vsm = mock(VerbatimSourceMapper.class);
    when(vsm.getMaxID(anyInt())).thenReturn(1);

    SqlSession session = mock(SqlSession.class);
    when(session.getMapper(TaxonMapper.class)).thenReturn(tm);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(session.getMapper(VerbatimSourceMapper.class)).thenReturn(vsm);
    vrm = mock(VerbatimRecordMapper.class);
    when(session.getMapper(VerbatimRecordMapper.class)).thenReturn(vrm);

    factory = mock(SqlSessionFactory.class);
    when(factory.openSession(anyBoolean())).thenReturn(session);
    when(factory.openSession(any(ExecutorType.class), anyBoolean())).thenReturn(session);
  }

  @Test
  public void applyDecision() {
    TreeBaseHandler h = new UselessHandler();
    Name n = Name.newBuilder().build();
    NameUsageBase u = new Synonym(n);
    final var orig = u.copy();

    EditorialDecision d = new EditorialDecision();
    d.setMode(EditorialDecision.Mode.UPDATE);

    assertEquals(orig, h.applyDecision(u, d).usage);

    d.setStatus(TaxonomicStatus.ACCEPTED);
    var updtd = h.applyDecision(u, d);
    assertNotEquals(orig, updtd);
    assertTrue(updtd.usage instanceof Taxon);
    assertEquals(TaxonomicStatus.ACCEPTED, updtd.usage.getStatus());
  }

  /**
   * A decision always carries an environment set, empty unless it changes environments.
   * Changing only the status must keep the environments of the taxon.
   */
  @Test
  public void applyDecisionEnvironments() {
    TreeBaseHandler h = new UselessHandler();
    Taxon t = new Taxon(Name.newBuilder().build());
    t.setStatus(TaxonomicStatus.ACCEPTED);
    t.setEnvironments(EnumSet.of(Environment.MARINE));

    EditorialDecision d = new EditorialDecision();
    d.setMode(EditorialDecision.Mode.UPDATE);
    d.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    var updtd = (Taxon) h.applyDecision(t, d).usage;
    assertEquals(TaxonomicStatus.PROVISIONALLY_ACCEPTED, updtd.getStatus());
    assertEquals(EnumSet.of(Environment.MARINE), updtd.getEnvironments());

    d.setEnvironments(EnumSet.of(Environment.FRESHWATER));
    updtd = (Taxon) h.applyDecision(t, d).usage;
    assertEquals(EnumSet.of(Environment.FRESHWATER), updtd.getEnvironments());
  }

  private static final int SOURCE = 1000;

  private static Sector blockingSector() {
    var s = Sector.newBuilder()
      .id(11)
      .datasetKey(3)
      .subjectDatasetKey(SOURCE)
      .entities(Set.of(EntityType.ANY))
      .ranks(Set.of())
      .build();
    s.setBlockedNames(Set.of("Aus bus"));
    s.setBlockedNamePatterns(Set.of("^Incertae"));
    s.setIssueExclusion(EnumSet.of(Issue.DOUBTFUL_NAME));
    return s;
  }

  private UselessHandler blockingHandler() {
    return new UselessHandler(1, Map.of(), factory, null, 0, blockingSector(), null, null, null, null);
  }

  private static Taxon taxon(String id, String sciname, Integer verbatimKey) {
    Name n = Name.newBuilder()
      .scientificName(sciname)
      .rank(Rank.SPECIES)
      .type(NameType.SCIENTIFIC)
      .verbatimKey(verbatimKey)
      .build();
    Taxon t = new Taxon(n);
    t.setId(id);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    return t;
  }

  /**
   * Blocked names apply to every tree sync, not only merges, and count like any other filter:
   * a blocked accepted name takes its synonyms with it.
   */
  @Test
  public void blockedNamesAreIgnoredAndCounted() {
    var h = blockingHandler();
    assertTrue(h.ignoreUsage(taxon("t1", "Aus bus", null), null, IssueContainer.VOID, false));
    assertTrue(h.ignoreUsage(taxon("t2", "Incertae sedis", null), null, IssueContainer.VOID, false));
    assertFalse(h.ignoreUsage(taxon("t3", "Aus cus", null), null, IssueContainer.VOID, false));
    assertEquals(2, (int) h.ignoredCounter.get(IgnoreReason.BLOCKED_NAME));

    Synonym syn = new Synonym(Name.newBuilder().scientificName("Aus bes").rank(Rank.SPECIES).type(NameType.SCIENTIFIC).build());
    syn.setId("s1");
    syn.setParentId("t1");
    syn.setStatus(TaxonomicStatus.SYNONYM);
    assertTrue(h.ignoreUsage(syn, null, IssueContainer.VOID, false));
    assertEquals(1, (int) h.ignoredCounter.get(IgnoreReason.IGNORED_PARENT));
  }

  /**
   * Excluded issues are looked up on the source name's verbatim record and never leak into the caller's issues,
   * which a kept usage would persist.
   */
  @Test
  public void excludedSourceIssues() {
    when(vrm.getIssues(any())).thenAnswer(inv -> {
      DSID<Integer> key = inv.getArgument(0);
      assertEquals(SOURCE, (int) key.getDatasetKey());
      var ic = IssueContainer.simple();
      ic.add(key.getId() == 7 ? Issue.DOUBTFUL_NAME : Issue.PARTIALLY_PARSABLE_NAME);
      return ic;
    });
    var h = blockingHandler();
    var issues = IssueContainer.simple();
    assertTrue(h.ignoreUsage(taxon("t1", "Cus dus", 7), null, issues, false));
    assertFalse(h.ignoreUsage(taxon("t2", "Dus eus", 8), null, issues, false));
    assertFalse(h.ignoreUsage(taxon("t3", "Eus fus", null), null, issues, false));
    assertFalse(issues.hasIssues());
    assertEquals(1, (int) h.ignoredCounter.get(IgnoreReason.ISSUE_EXCLUSION));
  }

  @Test
  public void reviewedDecisionOverridesExclusions() {
    var d = new EditorialDecision();
    d.setMode(EditorialDecision.Mode.REVIEWED);
    var h = blockingHandler();
    assertFalse(h.ignoreUsage(taxon("t1", "Aus bus", 7), d, IssueContainer.VOID, false));
    verifyNoInteractions(vrm);
  }

  private static final Sector SECTOR = Sector.newBuilder()
    .id(10)
    .entities(Set.of(EntityType.ANY))
    .ranks(Set.copyOf(Rank.DWC_RANKS))
    .build();
  class UselessHandler extends TreeBaseHandler {

    public UselessHandler() {
      super(1, null, factory, null, 0, SECTOR, null, null, null, null);
    }

    public UselessHandler(int targetDatasetKey, Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, int user, Sector sector, SectorImport state, Supplier<String> nameIdGen, Supplier<String> typeMaterialIdGen, UsageIdGen usageIdGen) {
      super(targetDatasetKey, decisions, factory, nameIndex, user, sector, state, nameIdGen, typeMaterialIdGen, usageIdGen);
    }

    @Override
    protected List<EditorialDecision> findParentDecisions(String taxonID) {
      return Collections.emptyList();
    }

    @Override
    protected Usage findExisting(Name n, Usage parent) {
      return null;
    }

    @Override
    protected void cacheImplicit(Taxon t) {

    }

    @Override
    public void acceptThrows(NameUsageBase obj) throws InterruptedException {

    }

    @Override
    public boolean hasThrown() {
      return false;
    }

    @Override
    public void copyRelations() {

    }

    @Override
    public Map<IgnoreReason, Integer> getIgnoredCounter() {
      return null;
    }

    @Override
    public int getDecisionCounter() {
      return 0;
    }
  }
}