package life.catalogue.exporter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.tree.NameUsageTreePrinter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Set;

public class HtmlExporter extends NameUsageTreePrinter {
  private static final Logger LOG = LoggerFactory.getLogger(HtmlExporter.class);

  private final HtmlWriter writer;
  private LoadingCache<String, Reference> refCache;

  private HtmlExporter(int datasetKey, String startID, Set<Rank> ranks, WsServerConfig cfg, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, null, startID, ranks, factory);
    this.writer = new HtmlWriter(writer, factory, "html", cfg, DSID.of(datasetKey, startID));
  }

  public static HtmlExporter subtree(int datasetKey, String rootID, WsServerConfig cfg, SqlSessionFactory factory, Writer writer) {
    return new HtmlExporter(datasetKey, rootID, null, cfg, factory, writer);
  }

  public static HtmlExporter subtree(int datasetKey, String rootID, Set<Rank> ranks, WsServerConfig cfg, SqlSessionFactory factory, Writer writer) {
    return new HtmlExporter(datasetKey, rootID, ranks, cfg, factory, writer);
  }

  void setupCache(SqlSession session) {
    final DSID<String> rKey = DSID.of(datasetKey, null);
    refCache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .build(new CacheLoader<>() {
         @Override
         public Reference load(String key) throws Exception {
           return session.getMapper(ReferenceMapper.class).get(rKey.id(key));
         }
      });
    LOG.info("Created reference cache for dataset {}", datasetKey);
  }

  @Override
  public int print() throws IOException {
    level=1;
    try (SqlSession session = factory.openSession(true)) {
      setupCache(session);
      writer.header();
      int count = super.print();
      writer.footer();
      return count;
    }
  }

  protected void start(NameUsageBase u) throws IOException {
    if (u.isSynonym()) {
      Synonym s = (Synonym) u;
      if (writer.info.getSynonyms() == null) {
        writer.info.setSynonyms(new ArrayList<>());
      }
      writer.info.getSynonyms().add(s);

    } else {
      if (writer.hasTaxon()) {
        writeTaxon();
      }

      writer.setTaxon(level, (Taxon) u);
    }
  }

  void writeTaxon() throws IOException {
    // load missing references and other infos
    TaxonDao.fillTaxonInfo(session, writer.info, refCache,
      false, true, true, false, true, false,
      false, false, false);
    writer.taxon();
  }

  protected void end(NameUsageBase u) throws IOException {
    if (writer.hasTaxon() && u.isTaxon() && writer.getLevel()==(level-1)) {
      writeTaxon();
    }
    if (u.isTaxon()) {
      writer.divClose();
    }
  }

  @Override
  protected void flush() throws IOException {
    writer.close();
  }
}
