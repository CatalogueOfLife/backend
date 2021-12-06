package life.catalogue.portal;

import com.google.common.base.Preconditions;

import freemarker.template.Template;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.exporter.FmUtil;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;

import freemarker.template.TemplateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.api.util.ObjectUtils.checkFound;

public class PortalPageRenderer {
  private static final Logger LOG = LoggerFactory.getLogger(PortalPageRenderer.class);

  public enum PortalPage {TAXON, DATASET};

  private final DatasetSourceDao sourceDao;
  private final TaxonDao tdao;
  private final LatestDatasetKeyCache cache;
  private final Map<PortalPage, Template> portalTemplates = new HashMap<>();
  private File portalTemplateDir = new File("/tmp");

  public PortalPageRenderer(DatasetSourceDao sourceDao, TaxonDao tdao, LatestDatasetKeyCache cache, File portalTemplateDir) throws IOException {
    this.sourceDao = sourceDao;
    this.tdao = tdao;
    this.cache = cache;
    setTemplateFolder(portalTemplateDir);
  }

  public File getPortalTemplateDir() {
    return portalTemplateDir;
  }

  public String renderTaxon(String id, boolean preview) throws TemplateException, IOException {
    final int datasetKey = releaseKey(preview);
    final Map<String, Object> data = buildData(datasetKey);

    final var key = new DSIDValue<>(datasetKey, id);
    //TODO: check if we had the id in a previous release...
    Taxon t = tdao.getOr404(key);
    TaxonInfo info = new TaxonInfo();
    data.put("info", info);
    info.setTaxon(t);
    try (SqlSession session = tdao.getFactory().openSession()) {
      tdao.fillTaxonInfo(session, info, null,
        true,
        true,
        false,
        true,
        false,
        false,
        false,
        true,
        false,
        false
      );
      // put source dataset title into the ID field so we can show a real title
      if (info.getSource() != null) {
        Dataset source = sourceDao.get(datasetKey, info.getSource().getSourceDatasetKey(), false);
        data.put("source", source);
      }
      if (info.getTaxon().getParentId() != null) {
        SimpleName parent = session.getMapper(NameUsageMapper.class).getSimple(DSID.of(datasetKey, info.getTaxon().getParentId()));
        data.put("parent", parent);
      }
      return render(PortalPage.TAXON, data);
    }
  }

  public String renderDatasource(int id, boolean preview) throws TemplateException, IOException {
    final int datasetKey = releaseKey(preview);
    final Map<String, Object> data = buildData(datasetKey);

    var d = checkFound(
      sourceDao.get(datasetKey, id, false),
      String.format("Datasource %s not found in release %s", id, datasetKey)
    );
    data.put("d", d);
    return render(PortalPage.DATASET, data);
  }

  private Map<String, Object> buildData(int datasetKey) {
    final Map<String, Object> data = new HashMap<>();
    data.put("releaseKey", datasetKey);
    return data;
  }

  private String render(PortalPage pp, Object data) throws TemplateException, IOException {
    var temp = portalTemplates.get(pp);
    Writer out = new StringWriter();
    temp.process(data, out);
    return out.toString();
  }

  public void setTemplateFolder(File portalTemplateDir) throws IOException {
    this.portalTemplateDir = Preconditions.checkNotNull(portalTemplateDir);
    if (!portalTemplateDir.exists()) {
      FileUtils.forceMkdir(portalTemplateDir);
    }
    for (PortalPage pp : PortalPage.values()) {
      loadTemplate(pp);
    }
  }

  private void loadTemplate(PortalPage pp) throws IOException {
    var f = template(pp);
    InputStream in;
    if (f.exists()) {
      LOG.info("Load {} portal template from {}", pp, portalTemplateDir);
      in = new FileInputStream(f);
    } else {
      LOG.info("Load {} portal template from resources", pp);
      in = getClass().getResourceAsStream("/freemarker-templates/portal/"+pp.name()+".ftl");
    }
    loadTemplate(pp, in);
  }

  private void loadTemplate(PortalPage pp, InputStream stream) throws IOException {
    try (BufferedReader br = UTF8IoUtils.readerFromStream(stream)) {
      Template temp = new Template(pp.name(), br, FmUtil.FMK);
      portalTemplates.put(pp, temp);
    }
  }

  public boolean store(PortalPage pp, String template) throws IOException {
    LOG.info("Store new portal page template {}", pp);
    try (Writer w = UTF8IoUtils.writerFromFile(template(pp))) {
      // enforce xhtml freemarker setting which we cannot keep in Jekyll
      w.write("<#ftl output_format=\"XHTML\">");
      IOUtils.write(template, w);
    }
    loadTemplate(pp);
    return true;
  }

  private File template(PortalPage pp) {
    return new File(portalTemplateDir, pp.name()+".ftl");
  }

  private int releaseKey(boolean preview) {
    Integer key = preview ? cache.getLatestReleaseCandidate(Datasets.COL) : cache.getLatestRelease(Datasets.COL);
    if (key == null) throw new NotFoundException("No COL" + (preview ? " preview" : "") + " release existing");
    return key;
  }
}
