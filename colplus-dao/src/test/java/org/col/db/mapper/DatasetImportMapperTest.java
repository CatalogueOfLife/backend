package org.col.db.mapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.vocab.*;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;


/**
 *
 */
public class DatasetImportMapperTest extends MapperTestBase<DatasetImportMapper> {
  private static Random rnd = new Random();

  public DatasetImportMapperTest() {
    super(DatasetImportMapper.class);
  }

  private static DatasetImport create(ImportState state) throws Exception {
    DatasetImport d = new DatasetImport();
    d.setDatasetKey(DATASET11.getKey());
    d.setError("no error");
    d.setState(state);
    d.setStarted(LocalDateTime.now());
    d.setFinished(LocalDateTime.now());
    d.setDownloadUri(URI.create("http://rs.gbif.org/datasets/nub.zip"));
    d.setDownload(LocalDateTime.now());
    d.setVerbatimCount(5748923);
    d.setNameCount(65432);
    d.setTaxonCount(5748329);
    d.setReferenceCount(9781);
    d.setDistributionCount(12345);
    d.setVernacularCount(432);
    d.setIssuesCount(mockCount(Issue.class));
    d.setNamesByOriginCount(mockCount(Origin.class));
    d.setNamesByRankCount(mockCount(Rank.class));
    d.setNamesByTypeCount(mockCount(NameType.class));
    d.setVernacularsByLanguageCount(mockCount(Language.class));
    d.setDistributionsByGazetteerCount(mockCount(Gazetteer.class));
    d.setUsagesByStatusCount(mockCount(TaxonomicStatus.class));
    d.setNamesByStatusCount(mockCount(NomStatus.class));
    d.setNameRelationsByTypeCount(mockCount(NomRelType.class));
    Map<Term, Integer> vcnt = new HashMap<>();
    vcnt.put(AcefTerm.AcceptedSpecies, 10);
    d.setVerbatimByTypeCount(vcnt);
    return d;
  }

  private static DatasetImport create() throws Exception {
    return create(ImportState.DOWNLOADING);
  }

  private static <T extends Enum> Map<T, Integer> mockCount(Class<T> clazz) {
    Map<T, Integer> cnt = Maps.newHashMap();
    for (T val : clazz.getEnumConstants()) {
      cnt.put(val, rnd.nextInt(Integer.MAX_VALUE));
    }
    return cnt;
  }

  @Test
  public void roundtrip() throws Exception {
    DatasetImport d1 = create();
    mapper().create(d1);
    commit();
    assertEquals((Integer)1, d1.getAttempt());

    DatasetImport d2 = mapper().last(d1.getDatasetKey());
    assertNotNull(d2.getAttempt());
    d1.setAttempt(d2.getAttempt());
    assertEquals(d1, d2);

    d1.setState(ImportState.FINISHED);
    d1.setError("no error at all");
    mapper().update(d1);
    assertNotEquals(d1, d2);

    d2 = mapper().last(d1.getDatasetKey());
    assertEquals(d1, d2);
    commit();
  }

  @Test
  public void lastSuccessful() throws Exception {
    DatasetImport d = create();
    mapper().create(d);
    assertNull(mapper().lastByState(ImportState.FINISHED, d.getDatasetKey()));

    d.setState(ImportState.FAILED);
    d.setError("damn error");
    mapper().update(d);
    assertNull(mapper().lastByState(ImportState.FINISHED, d.getDatasetKey()));

    d = create();
    d.setState(ImportState.DOWNLOADING);
    mapper().create(d);
    assertNull(mapper().lastByState(ImportState.FINISHED, d.getDatasetKey()));

    d.setState(ImportState.FINISHED);
    mapper().update(d);
    assertNotNull(mapper().lastByState(ImportState.FINISHED, d.getDatasetKey()));

    d = create();
    d.setState(ImportState.CANCELED);
    mapper().create(d);
    assertNotNull(mapper().lastByState(ImportState.FINISHED, d.getDatasetKey()));
    commit();
  }

  @Test
  public void listCount() throws Exception {
    mapper().create(create(ImportState.FAILED));
    mapper().create(create(ImportState.FINISHED));
    mapper().create(create(ImportState.PROCESSING));
    mapper().create(create(ImportState.FINISHED));
    mapper().create(create(ImportState.CANCELED));
    mapper().create(create(ImportState.INSERTING));
    mapper().create(create(ImportState.FINISHED));

    assertEquals(7, mapper().count(null));
    assertEquals(7, mapper().count(Lists.newArrayList()));
    assertEquals(1, mapper().count(Lists.newArrayList(ImportState.FAILED)));
    assertEquals(3, mapper().count(Lists.newArrayList(ImportState.FINISHED)));
    assertEquals(2, mapper().count(Lists.newArrayList(ImportState.PROCESSING, ImportState.INSERTING)));

    assertEquals(2, mapper().list(Lists.newArrayList(ImportState.PROCESSING, ImportState.INSERTING), new Page()).size());
  }

  @Test
  public void generate() throws Exception {
    DatasetImport d = mapper().metrics(DATASET11.getKey());
    assertEquals((Integer) 4, d.getNameCount());
    assertEquals((Integer) 2, d.getTaxonCount());
    assertEquals((Integer) 2, d.getReferenceCount());
    assertEquals((Integer) 5, d.getVerbatimCount());
    assertEquals((Integer) 3, d.getVernacularCount());
    assertEquals((Integer) 3, d.getDistributionCount());

    assertEquals( 6, d.getIssuesCount().keySet().size());
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.ESCAPED_CHARACTERS));
    assertEquals((Integer) 2, d.getIssuesCount().get(Issue.REFERENCE_ID_INVALID));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.ID_NOT_UNIQUE));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.URL_INVALID));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.INCONSISTENT_AUTHORSHIP));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.UNUSUAL_NAME_CHARACTERS));
    assertFalse(d.getIssuesCount().containsKey(Issue.NOT_INTERPRETED));
    assertFalse(d.getIssuesCount().containsKey(Issue.NULL_EPITHET));

    assertEquals( 1, d.getNamesByRankCount().size());
    assertEquals((Integer) 4, d.getNamesByRankCount().get(Rank.SPECIES));

    assertEquals( 1, d.getNamesByOriginCount().size());
    assertEquals((Integer) 4, d.getNamesByOriginCount().get(Origin.SOURCE));

    assertEquals( 1, d.getNamesByTypeCount().size());
    assertEquals((Integer) 4, d.getNamesByTypeCount().get(NameType.SCIENTIFIC));

    assertEquals( 1, d.getDistributionsByGazetteerCount().size());
    assertEquals((Integer) 3, d.getDistributionsByGazetteerCount().get(Gazetteer.TEXT));

    assertEquals( 3, d.getVernacularsByLanguageCount().size());
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.GERMAN));
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.ENGLISH));
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.DUTCH));

    assertEquals( 2, d.getUsagesByStatusCount().size());
    assertEquals((Integer) 2, d.getUsagesByStatusCount().get(TaxonomicStatus.ACCEPTED));
    assertEquals((Integer) 2, d.getUsagesByStatusCount().get(TaxonomicStatus.SYNONYM));

    assertEquals( 0, d.getNamesByStatusCount().size());

    assertEquals( 1, d.getNameRelationsByTypeCount().size());
    assertEquals((Integer) 1, d.getNameRelationsByTypeCount().get(NomRelType.SPELLING_CORRECTION));

    assertEquals( 0, d.getVerbatimByTypeCount().size());
  }

}