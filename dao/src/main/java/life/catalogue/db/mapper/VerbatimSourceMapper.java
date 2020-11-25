package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.SectorProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

/**
 *
 */
public interface VerbatimSourceMapper extends Create<VerbatimSource>, CopyDataset, DatasetProcessable<VerbatimSource>, SectorProcessable<VerbatimSource> {

  VerbatimSource get(@Param("key") DSID<String> key);
  
  IssueContainer getIssues(@Param("key") DSID<String> key);

  void update(@Param("key") DSID<String> key, @Param("issues") Set<Issue> issues);

}

