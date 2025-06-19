package life.catalogue.portal;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.SynonymException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.metadata.FmUtil;
import life.catalogue.resources.ResourceUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static life.catalogue.api.util.ObjectUtils.checkFound;

public class PortalPageRenderer {
  private static final Logger LOG = LoggerFactory.getLogger(PortalPageRenderer.class);

  public enum PortalPage {
    NOT_FOUND, TAXON, TOMBSTONE, DATASET, METADATA;
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
  private final List<DatasetRelease> annualReleases;
  private final List<DatasetRelease> annualXReleases;

  public PortalPageRenderer(DatasetDao datasetDao, DatasetSourceDao sourceDao, TaxonDao tdao, LatestDatasetKeyCache cache, Path portalTemplateDir) throws IOException {
    this.datasetDao = datasetDao;
    this.sourceDao = sourceDao;
    this.factory = tdao.getFactory();
    this.tdao = tdao;
    this.cache = cache;
    setTemplateFolder(portalTemplateDir);
    List<DatasetRelease> annuals = new ArrayList<>();;
    if (factory != null) {
      try (SqlSession session = factory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        annuals = dm.listReleasesQuick(Datasets.COL).stream()
          .filter(d -> !d.isDeleted())
          .filter(DatasetRelease::hasLongTermSupport)
          .collect(Collectors.toList());
      }
    }
    annualReleases = annuals.stream()
      .filter(d -> d.getOrigin()== DatasetOrigin.RELEASE)
      .collect(Collectors.toList());
    annualXReleases = annuals.stream()
      .filter(d -> d.getOrigin()== DatasetOrigin.XRELEASE)
      .collect(Collectors.toList());
  }

  public Path getPortalTemplateDir() {
    return portalTemplateDir;
  }

  /**
   * @param id a COL checklist taxon or synonym ID. In case of synonyms redirect to the taxon page.
   * @param env
   */
  public Response renderTaxon(String id, Environment env, boolean extended) throws TemplateException, IOException {
    final int datasetKey = releaseKey(env, extended);
    final Map<String, Object> data = buildData(datasetKey);

    try {
      final var key = new DSIDValue<>(datasetKey, id);
      //TODO: check if we had the id in a previous release...
      Taxon t = tdao.getOr404(key);
      UsageInfo info = new UsageInfo(t);
      data.put("info", info);
      try (SqlSession session = factory.openSession()) {
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
          false,
          false,
          false
        );
        // put source dataset title into the ID field so we can show a real title
        if (info.getSource() != null && info.getSource().getSourceDatasetKey() != null) {
          Dataset source = sourceDao.get(datasetKey, info.getSource().getSourceDatasetKey(), false);
          data.put("source", source);
        }
        if (info.getUsage().getParentId() != null) {
          SimpleName parent = session.getMapper(NameUsageMapper.class).getSimple(DSID.of(datasetKey, info.getUsage().getParentId()));
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
      data.put("first", datasetDao.getRelease(e.usage.getFirstReleaseKey()));
      data.put("last", datasetDao.getRelease(e.usage.getLastReleaseKey()));
      // load verbatim source from last release
      if (e.usage.getLastReleaseKey() != null) {
        var v = tdao.getSource(DSID.of(e.usage.getLastReleaseKey(), id));
        data.put("verbatim", v);
        data.put("source", v == null ? null : datasetDao.get(v.getSourceDatasetKey()));
      }
      // list all annual releases this id appears in
      List<DatasetRelease> appearsIn = new ArrayList<>();
      List<SimpleNameCached> alternatives = new ArrayList<>();
      if (factory != null) {
        try (SqlSession session = factory.openSession()) {
          var num = session.getMapper(NameUsageMapper.class);
          for (DatasetRelease r : annualReleases) {
            if (num.exists(DSID.of(r.getKey(), id))) {
              appearsIn.add(r);
            }
          }
          // load alternative names in case we switched authors
          var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
          var cNidx = amm.getCanonicalNidx(e.usage);
          if (cNidx != null) {
            alternatives = num.listByCanonNIDX(datasetKey, cNidx);
          }
        }
      }
      data.put("annualReleases", appearsIn);
      data.put("alternatives", alternatives);
      return render(env, PortalPage.TOMBSTONE, data);

    } catch (NotFoundException e) {
      return render(env, PortalPage.NOT_FOUND, new HashMap<>());
    }
  }

  public Response renderDatasource(int id, Environment env, boolean extended) throws TemplateException, IOException {
    try {
      final int datasetKey = releaseKey(env, extended);
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

  public Response renderMetadata(Environment env, boolean extended) throws TemplateException, IOException {
    try {
      final int datasetKey = releaseKey(env, extended);
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
      LOG.info("Load {} portal template from resources", pp);
      in = getClass().getResourceAsStream("/freemarker-templates/portal/"+pp.name()+".ftl");
    }
    loadTemplate(env, pp, in);
  }

  private void loadTemplate(Environment env, PortalPage pp, InputStream stream) throws IOException {
    try (BufferedReader br = UTF8IoUtils.readerFromStream(stream)) {
      Template temp = new Template(pp.name(), br, FmUtil.FMK);
      portalTemplates.get(env).put(pp, temp);
    }
  }

  public boolean store(Environment env, PortalPage pp, String template) throws IOException {
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

  private int releaseKey(Environment env, boolean extended) {
    boolean candidate = env != Environment.PROD;
    Integer key = candidate ? cache.getLatestReleaseCandidate(Datasets.COL, extended) : cache.getLatestRelease(Datasets.COL, extended);
    if (key == null) throw new NotFoundException("No COL " + (extended ? "X-":"") + "release " + (candidate ? "candidate " : "") + "existing");
    return key;
  }
}
