package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;
import org.gbif.dwc.terms.Term;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public interface VerbatimRecordMapper extends Create<VerbatimRecord>, DatasetProcessable<VerbatimRecord>, CopyDataset {
  
  /**
   * @param datasetKey the required dataset key
   * @param types rowTypes to restrict to
   * @param terms optional list of terms and their values to filter by
   * @param termOp logical operator to combine multiple term filters
   * @param issues optional issues to filter by
   * @param q full text search query on term values
   */
  int count(@Param("datasetKey") int datasetKey,
            @Nullable @Param("types") Collection<Term> types,
            @Nullable @Param("terms") Map<Term, String> terms,
            @Param("termOp") LogicalOperator termOp,
            @Nullable @Param("issues") Collection<Issue> issues,
            @Nullable @Param("q") String q
  );
  
  /**
   * List verbatim records for a given dataset
   * @param datasetKey the required dataset key
   * @param types rowTypes to restrict to
   * @param terms optional list of terms and their values to filter by
   * @param termOp logical operator to combine multiple term filters
   * @param issues optional issues to filter by
   * @param q full text search query on term values
   * @param page
   * @return
   */
  List<VerbatimRecord> list(@Param("datasetKey") int datasetKey,
                            @Nullable @Param("types") Collection<Term> types,
                            @Nullable @Param("terms") Map<Term, String> terms,
                            @Param("termOp") LogicalOperator termOp,
                            @Nullable @Param("issues") Collection<Issue> issues,
                            @Nullable @Param("q") String q,
                            @Param("page") Page page
  );
  
  VerbatimRecord get(@Param("key") DSID<Integer> key);
  
  IssueContainer getIssues(@Param("key") DSID<Integer> key);

  void update(@Param("key") DSID<Integer> key, @Param("issues") Set<Issue> issues);

}

