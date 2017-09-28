package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Page;
import org.col.api.VerbatimRecord;

import java.util.List;

/**
 *
 */
public interface VerbatimRecordMapper {

  int count(@Param("datasetKey") int datasetKey);

  List<VerbatimRecord> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  VerbatimRecord get(@Param("datasetKey") int datasetKey, @Param("id") String id);

  VerbatimRecord getByName(@Param("datasetKey") int datasetKey, @Param("id") String nameId);

  VerbatimRecord getByTaxon(@Param("datasetKey") int datasetKey, @Param("id") String taxonId);

  void create(@Param("rec") VerbatimRecord record, @Param("taxonKey") int taxonKey, @Param("nameKey") int nameKey);

}

