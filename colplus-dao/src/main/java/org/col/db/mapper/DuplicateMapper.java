package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.EqualityMode;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

public interface DuplicateMapper {
  
  List<Duplicate> find(@Param("datasetKey") int datasetKey,
                       @Param("mode") EqualityMode mode,
                       @Param("rank") Rank rank,
                       @Param("status1") TaxonomicStatus status1,
                       @Param("status2") TaxonomicStatus status2,
                       @Param("parentDifferent") Boolean parentDifferent,
                       @Param("page") Page page);
}
