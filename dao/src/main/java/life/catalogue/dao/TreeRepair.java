package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameVerbatim;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.NameUsageMapper;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repairs a broken classification of a project or release in place.
 */
public class TreeRepair {
  private static final Logger LOG = LoggerFactory.getLogger(TreeRepair.class);

  private TreeRepair() {
  }

  /**
   * Points every usage whose parent does not exist at the fallback parent instead and flags it:
   * {@link Issue#PARENT_ID_INVALID} for an accepted usage, {@link Issue#ACCEPTED_ID_INVALID} for a synonym, whose
   * parent is its accepted name.
   *
   * Postgres does not enforce the partitioned parent_id self reference, so deleting a parent - a sector re-sync
   * deletes all it imported - silently leaves its children behind. Whatever walks the classification cannot deal with
   * them: releases, the search index and the usage matchers.
   *
   * The caller commits the session.
   *
   * @param fallbackParentId the new parent of the repaired usages, null to make them roots
   * @return ids of the repaired usages
   */
  public static List<String> fixMissingParents(SqlSession session, int datasetKey, @Nullable String fallbackParentId, int user) {
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    List<String> missing = num.listMissingParentIds(datasetKey);
    if (missing == null || missing.isEmpty()) {
      return List.of();
    }
    LOG.warn("{} usages of dataset {} point at a parent that does not exist. Move them to {}", missing.size(), datasetKey,
      fallbackParentId == null ? "the root" : fallbackParentId);
    final IssueAdder adder = new IssueAdder(datasetKey, session);
    final DSID<String> key = DSID.root(datasetKey);
    for (String id : missing) {
      key.id(id);
      SimpleNameVerbatim u = num.getSimpleVerbatim(key);
      num.updateParentId(key, fallbackParentId, user);
      boolean synonym = u.getStatus() != null && u.getStatus().isSynonym();
      adder.addIssue(u.getVerbatimSourceKey(), id, synonym ? Issue.ACCEPTED_ID_INVALID : Issue.PARENT_ID_INVALID);
    }
    return missing;
  }
}
