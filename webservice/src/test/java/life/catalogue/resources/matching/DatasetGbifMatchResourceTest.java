package life.catalogue.resources.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.UsageMatcherMemStore;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.resources.matching.gbif.GbifNameUsageMatch;

import org.gbif.nameparser.api.Rank;

import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dataset scoped GBIF species match only serves datasets with a persistent matcher.
 */
public class DatasetGbifMatchResourceTest {
  static final UUID GBIF_KEY = UUID.fromString("7ddf754f-d193-4cc9-b351-99906754a03b");
  static final Map<String, Integer> NIDX = Map.of("Plantae", 1, "Pinaceae", 2, "Abies", 3, "Abies alba", 4);
  DatasetGbifMatchResource resource;

  static SimpleNameCached usage(String id, String parent, Rank rank, String name, String authorship) {
    var u = new SimpleNameCached(id, name, rank);
    u.setParent(parent);
    u.setStatus(TaxonomicStatus.ACCEPTED);
    u.setAuthorship(authorship);
    u.setNamesIndexId(NIDX.get(name));
    return u;
  }

  @Before
  public void init() throws Exception {
    NameIndex nidx = mock(NameIndex.class);
    when(nidx.match(any(Name.class), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
      Name n = inv.getArgument(0);
      Integer id = NIDX.get(n.getScientificName());
      return id == null ? NameMatch.noMatch() : NameMatch.match(id);
    });
    when(nidx.match(any(SimpleName.class), anyBoolean(), anyBoolean())).thenCallRealMethod();

    var store = new UsageMatcherMemStore(3);
    store.add(usage("P", null, Rank.KINGDOM, "Plantae", null));
    store.add(usage("F", "P", Rank.FAMILY, "Pinaceae", null));
    store.add(usage("G", "F", Rank.GENUS, "Abies", "Mill."));
    store.add(usage("S", "G", Rank.SPECIES, "Abies alba", "Mill."));

    // dataset 3 has a persistent matcher, dataset 4 none
    UsageMatcherFactory matchers = mock(UsageMatcherFactory.class);
    when(matchers.getNameIndex()).thenReturn(nidx);
    when(matchers.get(3)).thenReturn(new UsageMatcher(3, nidx, store, true));

    var d = new Dataset();
    d.setKey(3);
    d.setTitle("Firs");
    d.setGbifKey(GBIF_KEY);
    DatasetMapper dm = mock(DatasetMapper.class);
    when(dm.get(3)).thenReturn(d);
    SqlSession session = mock(SqlSession.class);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    SqlSessionFactory factory = mock(SqlSessionFactory.class);
    when(factory.openSession()).thenReturn(session);

    resource = new DatasetGbifMatchResource(matchers, factory);
  }

  GbifNameUsageMatch match(int datasetKey, String name) {
    return resource.match(datasetKey, null, null, name, null, null, null, null, null, null, null, null, null, false, false, new Classification());
  }

  @Test
  public void match() {
    var m = match(3, "Abies alba Mill.");
    assertEquals("EXACT", m.diagnostics.matchType);
    assertEquals("S", m.usage.key);

    var md = resource.metadata(3);
    assertEquals("3", md.mainIndex.clbDatasetKey);
    assertEquals(GBIF_KEY.toString(), md.mainIndex.datasetKey);
    assertEquals("Firs", md.mainIndex.datasetTitle);
    assertEquals(4, (int) md.mainIndex.nameUsageCount);
  }

  @Test
  public void noPersistentMatcher() {
    assertThrows(NotFoundException.class, () -> match(4, "Abies alba"));
    assertThrows(NotFoundException.class, () -> resource.metadata(4));
  }
}
