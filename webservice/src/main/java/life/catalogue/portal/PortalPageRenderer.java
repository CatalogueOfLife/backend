package life.catalogue.portal;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.SynonymException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.metadata.FmUtil;
import life.catalogue.resources.ResourceUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import freemarker.template.Template;
import freemarker.template.TemplateException;

import static life.catalogue.api.util.ObjectUtils.checkFound;

public class PortalPageRenderer {
  private static final Logger LOG = LoggerFactory.getLogger(PortalPageRenderer.class);

  public enum PortalPage {
    NOT_FOUND, TAXON, TOMBSTONE, DATASET, METADATA, CLB_DATASET;
    public boolean isChecklistBank() {
      return this == CLB_DATASET;
    }
  }

  public enum Environment {PROD, PREVIEW, DEV};

  private final SqlSessionFactory factory;
  private final DatasetDao datasetDao;
  private final DatasetSourceDao sourceDao;
  private final TaxonDao tdao;
  private final LatestDatasetKeyCache cache;
  private final Map<Environment, Map<PortalPage, Template>> portalTemplates = Map.copyOf(
    Arrays.stream(Environment.values())
          .collect(Collectors.toMap(e -> e, e -> new HashMap<>()))
  );
  private Path portalTemplateDir = Path.of("/tmp");

  public PortalPageRenderer(DatasetDao datasetDao, DatasetSourceDao sourceDao, TaxonDao tdao, LatestDatasetKeyCache cache, Path portalTemplateDir) throws IOException {
    this.datasetDao = datasetDao;
    this.sourceDao = sourceDao;
    this.factory = tdao.getFactory();
    this.tdao = tdao;
    this.cache = cache;
    setTemplateFolder(portalTemplateDir);
  }

  public Path getPortalTemplateDir() {
    return portalTemplateDir;
  }

  public Response renderClbDataset(int datasetKey, Environment env) throws TemplateException, IOException {
    try {
      var d = datasetDao.getOr404(datasetKey);
      return render(env, PortalPage.CLB_DATASET, d);

    } catch (NotFoundException e) {
      return Response
        .status(Response.Status.NOT_FOUND)
        .type(MediaType.TEXT_HTML)
        .entity(readClbIndexPage())
        .build();
    }
  }

  /**
   * @param id a COL checklist taxon or synonym ID. In case of synonyms redirect to the taxon page.
   * @param env
   */
  public Response renderTaxon(String id, Environment env) throws TemplateException, IOException {
    final int datasetKey = releaseKey(env);
    final Map<String, Object> data = buildData(datasetKey);

    try {
      final var key = new DSIDValue<>(datasetKey, id);
      //TODO: check if we had the id in a previous release...
      Taxon t = tdao.getOr404(key);
      UsageInfo info = new UsageInfo(t);
      data.put("info", info);
      try (SqlSession session = tdao.getFactory().openSession()) {
        tdao.fillUsageInfo(session, info, null,
          false,
          true,
          true,
          false,
          true,
          false,
          false,
          false,
          true,
          false,
          false,
          false,
          false
        );
        // put source dataset title into the ID field so we can show a real title
        if (info.getSource() != null && info.getSource().getSourceDatasetKey() != null) {
          Dataset source = sourceDao.get(datasetKey, info.getSource().getSourceDatasetKey(), false);
          data.put("source", source);
        }
        if (info.getTaxon().getParentId() != null) {
          SimpleName parent = session.getMapper(NameUsageMapper.class).getSimple(DSID.of(datasetKey, info.getTaxon().getParentId()));
          data.put("parent", parent);
        }
        return render(env, PortalPage.TAXON, data);
      }

    } catch (SynonymException e) {
      // redirect to accepted taxon, e.g.
      // https://www.catalogueoflife.org/data/taxon/4LQWB to /data/taxon/4LQWC
      URI location = URI.create("/data/taxon/" + e.acceptedKey.getId());
      throw ResourceUtils.redirect(location);

    } catch (ArchivedException e) {
      // render tombstone page
      data.put("usage", e.usage);
      data.put("first", datasetDao.get(e.usage.getFirstReleaseKey()));
      data.put("last", datasetDao.get(e.usage.getLastReleaseKey()));
      // load verbatim source from last release
      if (e.usage.getLastReleaseKey() != null) {
        var v = tdao.getSource(DSID.of(e.usage.getLastReleaseKey(), id));
        data.put("verbatim", v);
        data.put("source", v == null ? null : datasetDao.get(v.getSourceDatasetKey()));
      }
      return render(env, PortalPage.TOMBSTONE, data);

    } catch (NotFoundException e) {
      return render(env, PortalPage.NOT_FOUND, new HashMap<>());
    }
  }

  public Response renderDatasource(int id, Environment env) throws TemplateException, IOException {
    try {
      final int datasetKey = releaseKey(env);
      final Map<String, Object> data = buildData(datasetKey);

      var d = checkFound(
        sourceDao.get(datasetKey, id, false),
        String.format("Datasource %s not found in release %s", id, datasetKey)
      );
      data.put("source", d);
      return render(env, PortalPage.DATASET, data);

    } catch (NotFoundException e) {
      return render(env, PortalPage.NOT_FOUND, new HashMap<>());
    }
  }

  public Response renderMetadata(Environment env) throws TemplateException, IOException {
    try {
      final int datasetKey = releaseKey(env);
      final Map<String, Object> data = buildData(datasetKey);

      Dataset d;
      try (SqlSession session = factory.openSession()){
        d = session.getMapper(DatasetMapper.class).get(datasetKey);
      }
      checkFound(d, String.format("Release %s not found", datasetKey));
      data.put("dataset", d);
      return render(env, PortalPage.METADATA, data);

    } catch (NotFoundException e) {
      return render(env, PortalPage.NOT_FOUND, new HashMap<>());
    }
  }

  private Map<String, Object> buildData(int datasetKey) {
    final Map<String, Object> data = new HashMap<>();
    data.put("releaseKey", datasetKey);
    return data;
  }

  private Response render(Environment env, PortalPage pp, Object data) throws TemplateException, IOException {
    var temp = portalTemplates.get(env).get(pp);
    Writer out = new StringWriter();
    temp.process(data, out);

    return Response
      .status(pp == PortalPage.NOT_FOUND ? Response.Status.NOT_FOUND : Response.Status.OK)
      .type(MediaType.TEXT_HTML)
      .entity(out.toString())
      .build();
  }

  public void setTemplateFolder(Path portalTemplateDir) throws IOException {
    this.portalTemplateDir = Preconditions.checkNotNull(portalTemplateDir);
    if (!Files.exists(portalTemplateDir)) {
      Files.createDirectories(portalTemplateDir);
    }
    for (Environment env : Environment.values()) {
      for (PortalPage pp : PortalPage.values()) {
        loadTemplate(env, pp);
      }
    }
  }

  private void loadTemplate(Environment env, PortalPage pp) throws IOException {
    var p = template(env, pp);
    InputStream in;
    if (Files.exists(p)) {
      LOG.info("Load {} {} portal template from {}", env, pp, p);
      in = Files.newInputStream(p);
    } else {
      if (pp.isChecklistBank()) {
        LOG.info("Prepare checklistbank {} template from resources", pp);
        // for CLB pages we first need to inject the freemarker template
        var html = readClbIndexPage();
        // storing does the injection
        store(env, pp, html);
        // now read the generated file just as we do above
        in = Files.newInputStream(p);
      } else {
        LOG.info("Load {} portal template from resources", pp);
        in = getClass().getResourceAsStream("/freemarker-templates/portal/"+pp.name()+".ftl");
      }
    }
    loadTemplate(env, pp, in);
  }

  private String readClbIndexPage() {
    return readTemplateResource("clb/index.html");
  }

  private String readTemplateResource(String resourceName) {
    var in = getClass().getResourceAsStream("/freemarker-templates/portal/"+resourceName);
    return InputStreamUtils.readEntireStream(in);
  }

  private void loadTemplate(Environment env, PortalPage pp, InputStream stream) throws IOException {
    try (BufferedReader br = UTF8IoUtils.readerFromStream(stream)) {
      Template temp = new Template(pp.name(), br, FmUtil.FMK);
      portalTemplates.get(env).put(pp, temp);
    }
  }

  public boolean store(Environment env, PortalPage pp, String template) throws IOException {
    if (pp.isChecklistBank()) {
      // inject SEO!
      var seo = readTemplateResource("clb/"+pp.name().toLowerCase()+".ftl");
      template = template.replaceFirst("<!-- REPLACE_WITH_SEO -->", Matcher.quoteReplacement(seo));
    }
    LOG.info("Store new portal page template {}", pp);
    try (Writer w = UTF8IoUtils.writerFromPath(template(env, pp))) {
      // enforce xhtml freemarker setting which we cannot keep in Jekyll
      w.write("<#ftl output_format=\"XHTML\">");
      IOUtils.write(template, w);
    }
    loadTemplate(env, pp);
    return true;
  }

  private Path template(Environment env, PortalPage pp) {
    return portalTemplateDir.resolve(Path.of(env.name(), pp.name()+".ftl"));
  }

  private int releaseKey(Environment env) {
    boolean preview = env == Environment.PREVIEW;
    Integer key = preview ? cache.getLatestReleaseCandidate(Datasets.COL, false) : cache.getLatestRelease(Datasets.COL, false);
    if (key == null) throw new NotFoundException("No COL" + (preview ? " preview" : "") + " release existing");
    return key;
  }
}
