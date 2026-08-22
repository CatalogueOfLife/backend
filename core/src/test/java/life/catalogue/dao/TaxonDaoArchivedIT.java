package life.catalogue.dao;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.img.ThumborConfig;
import life.catalogue.img.ThumborService;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.nidx.NameIndexFactory;

import java.util.Map;
import java.util.Set;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.junit.Assert.*;

/**
 * Verifies that ids which existed in an earlier release but were removed since are reported
 * as archived - not as a plain 404 - so the portal can render a tombstone page.
 * See https://github.com/CatalogueOfLife/data/issues/1644 where old OTU ids (BOLD BINs that
 * were reissued under their source id) rendered a bare "not found" page instead.
 */
public class TaxonDaoArchivedIT {
  static final int PROJECT_KEY = 3;
  static final int RELEASE_KEY = 11;
  // an id present in name_usage_archive.csv, but in no release usage table
  static final String ARCHIVED_ID = "56TT9";

  // same as the idprovider data, but with a partition for the empty release 11 so we can query it
  static final TestDataRule.TestData TEST_DATA = new TestDataRule.TestData("idprovider", PROJECT_KEY,
    Map.of("name_usage_archive", Map.of(
      "dataset_key", PROJECT_KEY,
      "n_id", "xyz"
    )), Set.of(PROJECT_KEY, RELEASE_KEY), true);

  @ClassRule
  public static SqlSessionFactoryRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = new TestDataRule(TEST_DATA);

  static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  TaxonDao dao() {
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    var nDao = new NameDao(factory, NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    var mDao = new MetricsDao(factory);
    return new TaxonDao(factory, nDao, mDao, new ThumborService(new ThumborConfig()),
      NameUsageIndexService.passThru(), null, validator);
  }

  /**
   * The /taxon/{id} endpoint - archived ids have always been reported here.
   */
  @Test
  public void getOr404Archived() {
    try {
      dao().getOr404(DSID.of(RELEASE_KEY, ARCHIVED_ID));
      fail("expected an ArchivedException for " + ARCHIVED_ID);
    } catch (ArchivedException e) {
      assertEquals(ARCHIVED_ID, e.usage.getId());
      assertEquals((Integer) PROJECT_KEY, e.usage.getDatasetKey());
    }
  }

  /**
   * The /taxon/{id}/info endpoint the portal uses - used to throw a bare 404 without the archived usage.
   */
  @Test
  public void getUsageInfoOr404Archived() {
    try {
      dao().getUsageInfoOr404(DSID.of(RELEASE_KEY, ARCHIVED_ID));
      fail("expected an ArchivedException for " + ARCHIVED_ID);
    } catch (ArchivedException e) {
      assertEquals(ARCHIVED_ID, e.usage.getId());
      assertEquals((Integer) PROJECT_KEY, e.usage.getDatasetKey());
      assertNotNull("the tombstone needs the name to link to a successor", e.usage.getName());
      assertNotNull(e.usage.getName().getScientificName());
    }
  }

  /**
   * An id that never existed stays a plain 404 in both endpoints.
   */
  @Test
  public void unknownIdStays404() {
    for (var key : Set.of(DSID.of(RELEASE_KEY, "notThere"), DSID.of(PROJECT_KEY, "notThere"))) {
      try {
        dao().getUsageInfoOr404(key);
        fail("expected a NotFoundException for " + key);
      } catch (ArchivedException e) {
        fail("unexpected ArchivedException for " + key);
      } catch (NotFoundException e) {
        // expected
      }
    }
  }
}
