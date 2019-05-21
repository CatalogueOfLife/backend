package org.col.db.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.IssueContainer;
import org.col.api.model.Page;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.Term;

/**
 *
 */
public interface VerbatimRecordMapper {
  
  /**
   * See list method for parameters
   */
  int count(@Param("datasetKey") int datasetKey,
            @Nullable @Param("types") Collection<Term> types,
            @Nullable @Param("terms") Map<Term, String> terms,
            @Param("termOp") LogicalOperator termOp,
            @Nullable @Param("issues") Collection<Issue> issues
  );
  
  /**
   * List verbatim records for a given dataset
   * @param datasetKey the required dataset key
   * @param types rowTypes to restrict to
   * @param terms optional list of terms and their values to filter by
   * @param termOp logical operator to combine multiple term filters
   * @param issues optional issues to filter by
   * @param page
   * @return
   */
  List<VerbatimRecord> list(@Param("datasetKey") int datasetKey,
                            @Nullable @Param("types") Collection<Term> types,
                            @Nullable @Param("terms") Map<Term, String> terms,
                            @Param("termOp") LogicalOperator termOp,
                            @Nullable @Param("issues") Collection<Issue> issues,
                            @Param("page") Page page
  );
  
  VerbatimRecord get(@Param("datasetKey") int datasetKey, @Param("key") int key);
  
  IssueContainer getIssues(@Param("datasetKey") int datasetKey, @Param("key") int key);

  void create(VerbatimRecord record);
  
  void update(@Param("datasetKey") int datasetKey, @Param("key") int key, @Param("issues") Set<Issue> issues);
}

