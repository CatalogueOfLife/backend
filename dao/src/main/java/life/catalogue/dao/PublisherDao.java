package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.License;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.validation.Validator;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PublisherDao extends DatasetEntityDao<UUID, Publisher, PublisherMapper> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(PublisherDao.class);

  public PublisherDao(SqlSessionFactory factory, Validator validator) {
    super(false, factory, Publisher.class, PublisherMapper.class, validator);
  }

}
