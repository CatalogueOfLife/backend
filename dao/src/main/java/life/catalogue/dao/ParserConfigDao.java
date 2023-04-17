package life.catalogue.dao;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ParserConfig;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.vocab.Origin;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.ParserConfigMapper;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.ParsedName;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

  public int loadParserConfigs() {
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession session = factory.openSession(true)) {
      ParserConfigMapper pcm = session.getMapper(ParserConfigMapper.class);
      LOG.info("Start loading parser configs");
      PgUtils.consume(
        pcm::process,
        pc -> {
          addToParser(pc);
          counter.incrementAndGet();
        }
      );
      LOG.info("Loaded {} parser configs", counter);
    }
    return counter.get();
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

  public void putName(ParserConfig obj, int user) {
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
    addToParser(obj);
  }

  public static void addToParser(ParserConfig obj){
    LOG.debug("Add config for {}", obj.getId());
    // defaults
    obj.setOrigin(Origin.USER);
    if (obj.getType() == null) {
      obj.setType(NameType.SCIENTIFIC);
    }

    ParsedName pn = Name.toParsedName(obj);
    pn.setNomenclaturalNote(null);
    pn.setState(ParsedName.State.COMPLETE); // if we leave state None we get unparsed issues when parsing this name
    pn.setTaxonomicNote(obj.getTaxonomicNote());
    pn.setExtinct(obj.getExtinct());
    NameParser.PARSER.configs().setName(concat(obj.getScientificName(), obj.getAuthorship()), pn);
    // configure name without authorship and authorship standalone if we have that
    if (obj.getAuthorship() != null && obj.hasAuthorship()) {
      NameParser.PARSER.configs().setAuthorship(obj.getAuthorship(), pn);

      ParsedName pnNoAuthor = new ParsedName();
      pnNoAuthor.copy(pn);
      pnNoAuthor.setCombinationAuthorship(null);
      pnNoAuthor.setBasionymAuthorship(null);
      pnNoAuthor.setSanctioningAuthor(null);
      NameParser.PARSER.configs().setName(obj.getScientificName(), pnNoAuthor);
    }
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

}
