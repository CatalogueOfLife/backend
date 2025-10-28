package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.metadata.DoiResolver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Validator;

public class VerbatimDao {
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimDao.class);
  private final SqlSessionFactory factory;

  public VerbatimDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public int deleteOrphans(int datasetKey, int userKey) {
    LOG.info("Remove orphaned verbatim sources from dataset {}", datasetKey);
    try (SqlSession session = factory.openSession()) {
      var vsm = session.getMapper(VerbatimSourceMapper.class);
      int cnt = vsm.deleteOrphans(datasetKey);
      LOG.info("Removed {} orphan verbatim sources from dataset {} by user {}", cnt, datasetKey, userKey);
      return cnt;
    }
  }
}
