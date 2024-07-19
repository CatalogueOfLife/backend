package life.catalogue.dao;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ParserConfig;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.vocab.Origin;
import life.catalogue.db.mapper.ParserConfigMapper;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.ParsedName;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class ParserConfigDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ParserConfigDao.class);

  private final SqlSessionFactory factory;

  public ParserConfigDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  private static String concat(String name, String authorship) {
    return concat(name, ' ', authorship);
  }

  private static String concat(String name, char delimiter, String authorship) {
    if (!Strings.isNullOrEmpty(authorship)) {
      return name.trim() + delimiter +authorship.trim();
    }
    return name.trim();
  }

  public ParserConfig get(String id) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(ParserConfigMapper.class).get(id);
    }
  }

  public ResultPage<ParserConfig> search(QuerySearchRequest request, @Nullable Page page) {
    try (SqlSession session = factory.openSession(true)) {
      ParserConfigMapper pcm = session.getMapper(ParserConfigMapper.class);
      List<ParserConfig> result = pcm.search(request, page);
      return new ResultPage<>(page, result, () -> pcm.countSearch(request));
    } catch (Exception e) {
      LOG.error("Error searching parser configs", e);
      return ResultPage.empty();
    }
  }

  /**
   * Add a config to the parser, overwriting potentially existing one with the same ID
   */
  public void add(ParserConfig obj, int user) {
    // try to create an id based on given sciname and authorship
    if (obj.getId() == null) {
      obj.updateID();
    }
    // make sure by now we have an ID
    if (obj.getId() == null) {
      throw new IllegalArgumentException("ID or scientificName and authorship required");
    } else if (!obj.getId().contains("|")) {
      throw new IllegalArgumentException("ID must concatenate name and authorship with a pipe symbol");
    }
    obj.setCreatedBy(user);
    obj.setOrigin(Origin.USER);
    LOG.info("Add config for {} by user {}", obj.getId(), user);

    // default name type
    if (obj.getType() == null) {
      obj.setType(NameType.SCIENTIFIC);
    }
    // persists first
    try (SqlSession session = factory.openSession(true)) {
      ParserConfigMapper pcm = session.getMapper(ParserConfigMapper.class);
      pcm.delete(obj.getId());
      pcm.create(obj);
    }
    // update parser
    ParsedName pn = obj.toParsedName();
    NameParser.PARSER.configs().add(obj.getScientificName(), obj.getAuthorship(), pn);
  }

  public void deleteName(String id, int user) {
    Preconditions.checkNotNull(id, "ID required");
    Preconditions.checkArgument(id.contains("|"), "ID must concatenate name and authorship with a pipe symbol");
    LOG.info("Remove parser config {}", id);
    // persists first
    try (SqlSession session = factory.openSession(true)) {
      ParserConfigMapper pcm = session.getMapper(ParserConfigMapper.class);
      pcm.delete(id);
    }
    // update parser
    ParserConfig cfg = new ParserConfig();
    cfg.setId(id);
    NameParser.PARSER.configs().deleteName(concat(cfg.getScientificName(), cfg.getAuthorship()));
    // also remove the authorship and canonical name if it exists!
    if (cfg.getAuthorship() != null) {
      NameParser.PARSER.configs().deleteName(cfg.getScientificName());
      NameParser.PARSER.configs().deleteAuthorship(cfg.getAuthorship());
    }
  }

  public List<ParserConfig> list() {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(ParserConfigMapper.class).list();
    }
  }
}
