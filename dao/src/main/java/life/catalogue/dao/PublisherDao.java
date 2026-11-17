package life.catalogue.dao;

import jakarta.validation.Validator;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.db.mapper.PublisherMapper;

import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper to GBIF
 */
public class PublisherDao extends EntityDao<UUID, Publisher, PublisherMapper> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(PublisherDao.class);

  public PublisherDao(SqlSessionFactory factory, Validator validator) {
    super(false, factory, Publisher.class, PublisherMapper.class, validator);
  }

  /**
   * Lists publishers with the number of their datasets the user can read, all of them without a query.
   * @param sortBy defaults to relevance, which without a query sorts by datasets
   * @param userKey the user reading, null for anonymous and the admin magic key for admins
   */
  public ResultPage<PublisherInfo> list(@Nullable String q, @Nullable PublisherInfo.SortBy sortBy, @Nullable Integer userKey, @Nullable Page page) {
    final String query = StringUtils.trimToNull(q);
    final var sort = sortBy == null ? PublisherInfo.SortBy.RELEVANCE : sortBy;
    final Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      var pm = session.getMapper(PublisherMapper.class);
      return new ResultPage<>(p, pm.list(query, sort, userKey, p), () -> pm.count(query));
    }
  }

  /**
   * Counts the datasets of a publisher the user can read by origin, type and license.
   * @param userKey the user reading, null for anonymous and the admin magic key for admins
   * @throws NotFoundException if the publisher is unknown
   */
  public PublisherMetrics metrics(UUID key, @Nullable Integer userKey) {
    try (SqlSession session = factory.openSession()) {
      var pm = session.getMapper(PublisherMapper.class);
      if (!pm.exists(key)) {
        throw NotFoundException.notFound(Publisher.class, key);
      }
      var m = new PublisherMetrics(key);
      for (var g : pm.datasetGroups(key, userKey)) {
        m.add(g.origin, g.type, g.license, g.privat, g.count, g.usages);
      }
      return m;
    }
  }

  /**
   * Stores a GBIF organisation as it was read from the GBIF registry, creating or updating it.
   */
  public void upsert(Publisher p) {
    try (SqlSession session = factory.openSession(true)) {
      var pm = session.getMapper(PublisherMapper.class);
      var existing = pm.get(p.getKey());
      if (existing == null) {
        pm.create(p);
      } else if (!existing.equals(p)) {
        pm.update(p);
      }
    }
  }
}
