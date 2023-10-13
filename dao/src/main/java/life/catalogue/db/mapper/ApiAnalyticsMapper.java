package life.catalogue.db.mapper;

import life.catalogue.api.model.ApiAnalytics;
import life.catalogue.api.model.Page;
import life.catalogue.db.CRUD;

import java.time.LocalDateTime;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

public interface ApiAnalyticsMapper extends CRUD<Long,ApiAnalytics> {

  List<ApiAnalytics> list(@Nullable @Param("onlyEmptyResults") Boolean onlyEmptyResults,
                          @Nullable @Param("page") Page page);

  boolean exists(@Param("fromDateTime") LocalDateTime fromDateTime,
                 @Param("toDateTime") LocalDateTime toDateTime);

  ApiAnalytics getLatest();

}
