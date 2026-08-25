package life.catalogue.db.mapper;

import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.db.CRUD;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

/**
 * Mapper for the generic job table that records every background job ever submitted to the JobExecutor.
 */
public interface JobMapper extends CRUD<UUID, JobInfo> {

  List<JobInfo> search(@Param("req") JobSearchRequest req, @Param("page") Page page);

  int count(@Param("req") JobSearchRequest req);

  /**
   * Deletes a batch of finished job records that no import, sync or export metrics refer to and that are
   * older than their retention age, keeping the newest keepPerClass records of every job class.
   *
   * @param defaultDays retention age in days for job classes without an override
   * @param overrides per job class retention days, keyed by simple class name and matched case insensitively
   * @param keepPerClass newest records to keep per job class regardless of age
   * @param limit maximum records to delete in this call
   * @return the number of job records deleted, which is exactly min(limit, remaining) - so a caller can
   *         loop while this returns limit
   */
  int deleteOld(@Param("defaultDays") int defaultDays,
                @Param("overrides") Map<String, Integer> overrides,
                @Param("keepPerClass") int keepPerClass,
                @Param("limit") int limit);

  /**
   * Lists all jobs that are still waiting, blocked or running.
   */
  List<JobInfo> listStale();

  /**
   * Marks all jobs that are still waiting, blocked or running as canceled.
   * To be used on startup to clean up jobs that were lost in a shutdown or crash.
   * @return number of jobs updated
   */
  int cancelStale();
}
