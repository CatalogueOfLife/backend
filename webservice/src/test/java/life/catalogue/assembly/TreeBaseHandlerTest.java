package life.catalogue.assembly;

import life.catalogue.api.model.*;

import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.IgnoreReason;

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.matching.NameIndex;

import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.NameIndexImplTest;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TreeBaseHandlerTest {

  SqlSessionFactory factory;

  @Before
  public void setup() throws Exception {
    TaxonMapper tm = mock(TaxonMapper.class);
    DatasetMapper dm = mock(DatasetMapper.class);

    SqlSession session = mock(SqlSession.class);
    when(session.getMapper(TaxonMapper.class)).thenReturn(tm);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);

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

  private static final Sector SECTOR = Sector.newBuilder()
    .id(10)
    .entities(Set.of(EntityType.ANY))
    .ranks(Set.copyOf(Rank.DWC_RANKS))
    .build();
  class UselessHandler extends TreeBaseHandler {

    public UselessHandler() {
      super(1, null, factory, null, null, SECTOR, null, null, null, null);
    }

    public UselessHandler(int targetDatasetKey, Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, User user, Sector sector, SectorImport state, Supplier<String> nameIdGen, Supplier<String> usageIdGen, Supplier<String> typeMaterialIdGen) {
      super(targetDatasetKey, decisions, factory, nameIndex, user, sector, state, nameIdGen, usageIdGen, typeMaterialIdGen);
    }

    @Override
    protected Usage findExisting(Name n, Usage parent) {
      return null;
    }

    @Override
    protected void cacheImplicit(Taxon t, Usage parent) {

    }

    @Override
    public void acceptThrows(NameUsageBase obj) throws InterruptedException {

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