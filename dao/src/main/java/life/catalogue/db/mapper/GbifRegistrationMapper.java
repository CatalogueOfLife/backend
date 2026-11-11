package life.catalogue.db.mapper;

import life.catalogue.api.model.GbifRegistration;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

/**
 * The datasets ChecklistBank registered itself in the GBIF registry.
 * Write them through DatasetDao.updateGbif, which keeps the dataset's GBIF keys and the outbox in step.
 */
public interface GbifRegistrationMapper {

  @Nullable
  GbifRegistration get(@Param("datasetKey") int datasetKey);

  boolean exists(@Param("datasetKey") int datasetKey);

  List<GbifRegistration> list();

  void create(GbifRegistration registration);

  int delete(@Param("datasetKey") int datasetKey);
}
