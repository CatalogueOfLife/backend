package life.catalogue.portal;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.io.PathUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.TaxonDao;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static life.catalogue.portal.PortalPageRenderer.Environment.PROD;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PortalPageRendererTest {

  final int projectKey= Datasets.COL;
  final int releaseKey=1000;
  final DSID<String> ID_DEAD = DSID.of(releaseKey, "DEAD");

  @Mock
  LatestDatasetKeyCache cache;
  @Mock
  DatasetDao ddao;
  @Mock
  TaxonDao tdao;
  @Mock
  DatasetSourceDao sdao;

  PortalPageRenderer renderer;

  @Before
  public void init() throws IOException {
    when(cache.getLatestRelease(projectKey, false)).thenReturn(releaseKey);

    var ds = new Dataset();
    ds.setKey(100);
    ds.setAlias("source");
    ds.setTitle("Super Source");
    when(ddao.get(ds.getKey())).thenReturn(ds);

    var dr1 = new Dataset();
    dr1.setKey(releaseKey-10);
    dr1.setAlias("rel1");
    dr1.setTitle("First Release");
    when(ddao.get(dr1.getKey())).thenReturn(dr1);

    var dr2 = new Dataset();
    dr2.setKey(releaseKey-2);
    dr2.setAlias("rel2");
    dr2.setTitle("Last Release");
    when(ddao.get(dr2.getKey())).thenReturn(dr2);

    ArchivedNameUsage anu = new ArchivedNameUsage();
    anu.setId(ID_DEAD.getId());
    anu.setDatasetKey(projectKey);
    anu.setLastReleaseKey(dr1.getKey());
    anu.setFirstReleaseKey(dr2.getKey());
    anu.setStatus(TaxonomicStatus.ACCEPTED);
    Name n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("Abies alba");
    n.setAuthorship("Mill.");
    anu.setName(n);
    anu.setClassification(List.of(
      SimpleName.sn("k1", Rank.KINGDOM, "Plantae", null),
      SimpleName.sn("f1", Rank.FAMILY, "Pinaceae", null),
      SimpleName.sn("g1", Rank.GENUS, "Abies", "Miller")
    ));
    when(tdao.getOr404(ID_DEAD)).thenThrow(new ArchivedException(ID_DEAD, anu));

    var v = new VerbatimSource();
    v.setDatasetKey(anu.getLastReleaseKey());
    v.setSourceDatasetKey(ds.getKey());
    when(tdao.getSource(any())).thenReturn(v);
    var p = Path.of("/tmp/col/templates");
    PathUtils.deleteRecursively(p);
    renderer = new PortalPageRenderer(ddao, sdao, tdao, cache, p);
  }

  @After
  public void after() throws IOException {
    PathUtils.deleteRecursively(renderer.getPortalTemplateDir());
  }

  @Test
  public void renderTombstone() throws Exception {
    var res = renderer.renderTaxon(ID_DEAD.getId(), PROD, false);
    assertEquals(HttpStatus.SC_OK, res.getStatus());
    System.out.println(res.getEntity().toString());
  }

}