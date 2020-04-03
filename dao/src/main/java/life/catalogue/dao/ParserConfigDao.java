package life.catalogue.dao;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.db.mapper.ParserConfigMapper;
import life.catalogue.parser.NameParser;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.ParsedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ParserConfigDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ParserConfigDao.class);

  private final SqlSessionFactory factory;

  public ParserConfigDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  private static String concat(String name, String authorship) {
    if (!Strings.isNullOrEmpty(authorship)) {
      return name.trim() + " " +authorship.trim();
    }
    return name.trim();
  }

  public int loadParserConfigs() {
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession session = factory.openSession(true)) {
      ParserConfigMapper pcm = session.getMapper(ParserConfigMapper.class);
      LOG.info("Start loading parser configs");
      pcm.process().forEach(pc -> {
        addToParser(pc);
        counter.incrementAndGet();
      });
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
    Preconditions.checkNotNull(obj.getId(), "ID required");
    Preconditions.checkArgument(obj.getId().contains("|"), "ID must concatenate name and authorship with a pipe symbol");
    obj.setCreatedBy(user);
    // persists first
    try (SqlSession session = factory.openSession(true)) {
      ParserConfigMapper pcm = session.getMapper(ParserConfigMapper.class);
      pcm.delete(obj.getId());
      pcm.create(obj);
    }
    // update parser
    addToParser(obj);
  }

  private void addToParser(ParserConfig obj){
    LOG.info("Create parser config {}", obj.getId());
    ParsedName pn = Name.toParsedName(obj);
    pn.setTaxonomicNote(obj.getTaxonomicNote());
    NameParser.configs().setName(concat(obj.getScientificName(), obj.getAuthorship()), pn);
    // configure name without authorship and authorship standalone if we have that
    if (obj.getAuthorship() != null && obj.hasAuthorship()) {
      NameParser.configs().setAuthorship(obj.getAuthorship(), pn);

      ParsedName pnNoAuthor = new ParsedName();
      pnNoAuthor.copy(pn);
      pnNoAuthor.setCombinationAuthorship(null);
      pnNoAuthor.setBasionymAuthorship(null);
      pnNoAuthor.setSanctioningAuthor(null);
      NameParser.configs().setName(obj.getScientificName(), pnNoAuthor);
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
    ParsedName pn = NameParser.configs().deleteName(concat(cfg.getScientificName(), cfg.getAuthorship()));
    // also remove the authorship if exists!
    if (cfg.getAuthorship() != null) {
      NameParser.configs().deleteAuthorship(cfg.getAuthorship());
    }
  }

}
