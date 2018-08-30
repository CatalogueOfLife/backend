package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.Term;

/**
 *
 */
public interface VerbatimRecordMapper {

  int count(@Param("datasetKey") int datasetKey, @Param("type") Term type);

  List<TermRecord> list(@Param("datasetKey") int datasetKey, @Param("type") Term type, @Param("page") Page page);

  TermRecord get(@Param("datasetKey") int datasetKey, @Param("key") int key);

  void create(TermRecord record);

}

