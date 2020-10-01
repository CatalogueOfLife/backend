package life.catalogue.exporter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import freemarker.template.*;
import life.catalogue.api.model.*;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.tree.NameUsageTreePrinter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class HtmlExporter extends NameUsageTreePrinter {
  private static final Logger LOG = LoggerFactory.getLogger(HtmlExporter.class);
  private static final Version freemarkerVersion = Configuration.VERSION_2_3_28;
  private static final Configuration fmk = new Configuration(freemarkerVersion);
  static {
    fmk.setClassForTemplateLoading(AcExporter.class, "/exporter/html");
    // see https://freemarker.apache.org/docs/pgui_quickstart_createconfiguration.html
    fmk.setDefaultEncoding("UTF-8");
    fmk.setDateFormat("yyyy-MM-dd");
    fmk.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    fmk.setLogTemplateExceptions(false);
    fmk.setWrapUncheckedExceptions(true);
    // allow the use of java8 dates
    fmk.setObjectWrapper(new LocalDateObjectWrapper(freemarkerVersion));
  }

  private final Writer writer;
  private Dataset dataset;
  private TaxonInfo taxon;
  private int taxonLevel;
  private Map<String, Object> data = new HashMap<>();
  private LoadingCache<String, Reference> refCache;

  private HtmlExporter(int datasetKey, String startID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, null, startID, ranks, factory);
    this.writer = writer;
  }

  public static HtmlExporter subtree(int datasetKey, String rootID, SqlSessionFactory factory, Writer writer) {
    return new HtmlExporter(datasetKey, rootID, null, factory, writer);
  }

  public static HtmlExporter subtree(int datasetKey, String rootID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    return new HtmlExporter(datasetKey, rootID, ranks, factory, writer);
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
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      setupCache(session);
      dataset = dm.get(datasetKey);
      data.put("d", dataset);
      List<Taxon> parents = tm.classification(DSID.of(datasetKey, startID));
      data.put("classification", Lists.reverse(parents));
      Template temp = fmk.getTemplate("header.ftl");
      temp.process(data, writer);

      setupCache(session);
      int count = super.print();

      temp = fmk.getTemplate("footer.ftl");
      temp.process(data, writer);
      return count;

    } catch (TemplateException e) {
      throw new IOException(e);
    }
  }

  protected void start(NameUsageBase u) throws IOException {
    if (u.isSynonym()) {
      Synonym s = (Synonym) u;
      if (taxon.getSynonyms() == null) {
        taxon.setSynonyms(new ArrayList<>());
      }
      taxon.getSynonyms().add(s);

    } else {
      if (taxon != null) {
        writeTaxon();
      }

      taxonLevel = level;
      taxon = new TaxonInfo();
      Taxon t = (Taxon) u;
      taxon.setTaxon(t);
    }
    //System.out.println(StringUtils.repeat(' ', level) + ">" + status(u));
  }

  void writeTaxon() throws IOException {
    //System.out.println(taxon.getTaxon().getLabel());
    // load missing references
    TaxonDao.fillTaxonInfo(session, taxon, refCache, false, true, true, false, true, false);
    // now print the full thing
    data.put("t", taxon);
    int cssLevel = 6;
    if (!taxon.getTaxon().getName().getRank().isSpeciesOrBelow()) {
      cssLevel = Math.min(taxonLevel,6);
    }
    data.put("level", cssLevel);

    try {
      Template temp = fmk.getTemplate("taxon.ftl");
      temp.process(data, writer);
    } catch (TemplateException e) {
      throw new IOException(e);
    }
    taxon = null;
  }

  static char status(NameUsageBase u) {
    return u.isSynonym() ? 'S' : 'T';
  }

  protected void end(NameUsageBase u) throws IOException {
    if (taxon != null && u.isTaxon() && taxonLevel==(level-1)) {
      writeTaxon();
    }
    //System.out.println(StringUtils.repeat(' ', level-1) + "<" + status(u));
    if (u.isTaxon()) {
      writer.write("</div>\n");
    }
  }

  @Override
  protected void flush() throws IOException {
    writer.flush();
  }
}
