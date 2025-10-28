package life.catalogue.basgroup;

import life.catalogue.TestUtils;
import life.catalogue.api.model.LinneanNameUsage;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.VerbatimSourceMapper;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HomotypicConsolidatorTest {

  @Test
  public void findPrimaryUsage() throws Exception {
    var vsm = mock(VerbatimSourceMapper.class);
    when(vsm.getMaxID(anyInt())).thenReturn(100);

    var session = mock(SqlSession.class);
    when(session.getMapper(VerbatimSourceMapper.class)).thenReturn(vsm);

    var factory = mock(SqlSessionFactory.class);
    when(factory.openSession(anyBoolean())).thenReturn(session);

    var infoCache = TestUtils.mockedInfoCache();
    var info = new DatasetInfoCache.DatasetInfo(3, DatasetOrigin.PROJECT,3, null, false);
    when(infoCache.info(anyInt())).thenReturn(info);

    var hc = HomotypicConsolidator.forTaxa(factory, 3, List.of(), u -> 1);

    var bg = new HomotypicGroup<LinneanNameUsage>(null, "sapiens", Authorship.authors("Linnaeus"), NomCode.ZOOLOGICAL);
    bg.setBasionym(lnu("1", Rank.SUBSPECIES, "Nasua olivacea quitensis", "Lönnberg, 1913"));
    bg.addRecombination(lnu("2", Rank.SUBSPECIES, "Nasuella olivacea quitensis", "(Lönnberg, 1913)", TaxonomicStatus.SYNONYM, "1"));
    var primary = hc.findPrimaryUsage(bg);
    assertEquals("1", primary.getId());
  }

  public static LinneanNameUsage lnu(String id, Rank rank, String name, String authorship) {
    return lnu(id, rank, name, authorship, TaxonomicStatus.ACCEPTED, null);
  }
  public static LinneanNameUsage lnu(String id, Rank rank, String name, String authorship, TaxonomicStatus status, String parentID) {
    LinneanNameUsage lnu = new LinneanNameUsage();
    lnu.setId(id);
    lnu.setParentId(parentID);
    lnu.setRank(rank);
    lnu.setAuthorship(authorship);
    lnu.setScientificName(name);
    lnu.setStatus(status);
    return lnu;
  }
}