package org.col.db.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.EqualityMode;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

public interface DuplicateMapper {
  
  List<Object> listKeys(@Param("datasetKey") int datasetKey,
                       @Param("mode") EqualityMode mode,
                       @Param("rank") Rank rank,
                       @Param("status") Set<TaxonomicStatus> status,
                       @Param("parentDifferent") Boolean parentDifferent,
                       @Param("withDecision") Boolean withDecision,
                       @Param("page") Page page);
  
  List<Duplicate> listUsages(@Param("datasetKey") int datasetKey,
                       @Param("mode") EqualityMode mode,
                       @Param("rank") Rank rank,
                       @Param("status") Set<TaxonomicStatus> status,
                       @Param("keys") List<Object> keys);
}
