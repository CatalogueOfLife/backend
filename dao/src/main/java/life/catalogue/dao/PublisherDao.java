package life.catalogue.dao;

import jakarta.validation.Validator;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.Publisher;
import life.catalogue.api.model.SectorPublisher;
import life.catalogue.config.GbifConfig;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import life.catalogue.db.mapper.PublisherMapper;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Wrapper to GBIF
 */
public class PublisherDao extends EntityDao<UUID, Publisher, PublisherMapper> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(PublisherDao.class);

  public PublisherDao(SqlSessionFactory factory, Validator validator) {
    super(false, factory, Publisher.class, PublisherMapper.class, validator);
  }

  public List<Publisher> search(String q) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(PublisherMapper.class).search(q, new Page(0, 100));
    }
  }
}
