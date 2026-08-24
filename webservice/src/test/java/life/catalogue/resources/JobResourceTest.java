package life.catalogue.resources;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class JobResourceTest {

  /**
   * The job types served by /job/types come from a classpath scan, so they silently go stale
   * if the scan stops finding the job packages. Assert on a few known jobs from different modules.
   */
  @Test
  public void scanJobTypes() {
    List<String> types = JobResource.scanJobTypes();
    assertTrue("expected a good number of job types, but got " + types, types.size() > 20);
    // jobs from the dao, core and importer modules
    assertTrue(types.contains("ImportJob"));
    assertTrue(types.contains("SectorSync"));
    assertTrue(types.contains("SectorDelete"));
    assertTrue(types.contains("DeleteDatasetJob"));
    assertTrue(types.contains("GbifSyncJob"));
    // abstract base classes are not job types - they never appear in job_class
    assertFalse(types.contains("BackgroundJob"));
    assertFalse(types.contains("DatasetJob"));
    assertFalse(types.contains("DatasetBlockingJob"));
    assertFalse(types.contains("GlobalBlockingJob"));
    assertFalse(types.contains("SectorRunnable"));
    assertFalse(types.contains("AbstractProjectCopy"));
    // sorted and free of duplicates
    assertEquals(types.stream().sorted().toList(), types);
    assertEquals(types.stream().distinct().count(), types.size());
  }
}
