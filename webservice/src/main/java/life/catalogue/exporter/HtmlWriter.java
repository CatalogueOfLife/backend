package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.metadata.FmUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.collect.Lists;

import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Print an entire dataset in a nested SimpleName json array.
 */
public class HtmlWriter implements AutoCloseable {
  private final Writer writer;
  private final String path;
  private final Dataset dataset;
  private final Taxon root;
  private final List<SimpleName> classification;
  TaxonInfo info = new TaxonInfo();
  private int level;
  private int cssLevel;
  private String css;

  public HtmlWriter(Writer writer, SqlSessionFactory factory, String fmkPath, WsServerConfig cfg, DSID<String> key) {
    this.writer = writer;
    this.path = fmkPath;
    this.css = cfg.exportCss;
    try (SqlSession session = factory.openSession(true)) {
      dataset = session.getMapper(DatasetMapper.class).get(key.getDatasetKey());
      if (dataset == null) {
        throw NotFoundException.notFound(Dataset.class, key.getDatasetKey());
      }
      if (dataset.getTitle() == null) {
        dataset.setTitle("ChecklistBank dataset " + key.getDatasetKey());
      }

      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      root = tm.get(key);
      if (root == null) {
        throw NotFoundException.notFound(Taxon.class, key);
      }
      classification = Lists.reverse(tm.classificationSimple(key));
    }
  }

  public String getCss() {
    return css;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public Taxon getRoot() {
    return root;
  }

  public List<SimpleName> getClassification() {
    return classification;
  }

  public TaxonInfo getInfo() {
    return info;
  }

  public int getLevel() {
    return level;
  }

  public int getCssLevel() {
    return cssLevel;
  }

  void setTaxon(int level, Taxon taxon) {
    this.level = level;
    cssLevel = 5;
    if (!taxon.getName().getRank().isSpeciesOrBelow()) {
      cssLevel = Math.min(this.level,5);
    }
    info = new TaxonInfo(taxon);
  }

  boolean hasTaxon() {
    return info != null && info.getTaxon() != null;
  }

  void writeTemplate(String name) throws IOException {
    try {
      Template temp = FmUtil.FMK.getTemplate(name);
      temp.process(this, writer);
    } catch (TemplateException e) {
      throw new IOException(e);
    }
  }

  void header() throws IOException {
    writeTemplate(path+ "/header.ftl");
  }

  void footer() throws IOException {
    writeTemplate(path + "/footer.ftl");
  }

  void taxon() throws IOException {
    if (hasTaxon()) {
      try {
        Template temp = FmUtil.FMK.getTemplate(path + "/taxon.ftl");
        temp.process(this, writer);
      } catch (TemplateException e) {
        throw new IOException(e);
      }
      info = null;
    }
  }

  void divClose() throws IOException {
    writer.write("</div>\n");
  }

  @Override
  public void close() throws IOException {
    writer.flush();
  }
}
