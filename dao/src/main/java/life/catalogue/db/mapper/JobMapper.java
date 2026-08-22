package life.catalogue.db.mapper;

import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.db.CRUD;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

/**
 * Mapper for the generic job table that records every background job ever submitted to the JobExecutor.
 */
public interface JobMapper extends CRUD<UUID, JobInfo> {

  List<JobInfo> search(@Param("req") JobSearchRequest req, @Param("page") Page page);

  int count(@Param("req") JobSearchRequest req);

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
