package life.catalogue.exporter;

import com.google.common.collect.Lists;
import freemarker.template.*;
import life.catalogue.api.model.*;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.tree.EventTreePrinter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class HtmlExporter extends EventTreePrinter {
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

  private Dataset dataset;
  private TaxonInfo taxon;
  private int taxonLevel;
  private Map<String, Object> data = new HashMap<>();

  private HtmlExporter(int datasetKey, String startID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, null, startID, ranks, factory, writer);
  }

  public static HtmlExporter subtree(int datasetKey, String rootID, SqlSessionFactory factory, Writer writer) {
    return new HtmlExporter(datasetKey, rootID, null, factory, writer);
  }

  public static HtmlExporter subtree(int datasetKey, String rootID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    return new HtmlExporter(datasetKey, rootID, ranks, factory, writer);
  }

  @Override
  public int print() throws IOException {
    level=1;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      TaxonMapper tm = session.getMapper(TaxonMapper.class);

      dataset = dm.get(datasetKey);
      data.put("d", dataset);
      List<Taxon> parents = tm.classification(DSID.of(datasetKey, startID));
      data.put("classification", Lists.reverse(parents));
      Template temp = fmk.getTemplate("header.ftl");
      temp.process(data, writer);

      int count = super.print();

      temp = fmk.getTemplate("footer.ftl");
      temp.process(data, writer);
      return count;

    } catch (TemplateException e) {
      throw new IOException(e);
    }
  }

  private static void populateUsage(SimpleName u, NameUsageBase nu) {
    Name n = new Name();
    nu.setName(n);
    nu.setId(u.getId());
    n.setScientificName(u.getName());
    n.setAuthorship(u.getAuthorship());
    n.setRank(u.getRank());
    nu.setStatus(u.getStatus());
    n.setCode(u.getCode());
  }

  protected void startEvent(SimpleName u) throws IOException {
    if (u.getStatus().isSynonym()) {
      if (taxon.getSynonyms() == null) {
        taxon.setSynonyms(new ArrayList<>());
      }
      Synonym s = new Synonym();
      populateUsage(u, s);
      taxon.getSynonyms().add(s);

    } else {
      // print previous?
      if (taxon != null) {
        writeTaxon();
      }

      taxon = new TaxonInfo();
      Taxon t = new Taxon();
      populateUsage(u, t);
      taxon.setTaxon(t);
      if (taxon.getTaxon().getName().getRank().isSpeciesOrBelow()) {
        taxonLevel = 6;
      } else {
        taxonLevel = Math.min(level,6);
      }
    }
  }

  void writeTaxon() throws IOException {
    // now print the full thing
    data.put("t", taxon);
    data.put("level", taxonLevel);

    try {
      Template temp = fmk.getTemplate("taxon.ftl");
      temp.process(data, writer);
    } catch (TemplateException e) {
      throw new IOException(e);
    }
  }

  protected void endEvent(SimpleName u) throws IOException {
    if (!u.getStatus().isSynonym()) {
      writer.write("</div>\n");
    }
  }

}
