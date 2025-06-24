package life.catalogue.portal;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.SynonymException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Environment;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetInfoCache;
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

  private static final String RELEASE_KEY_FILE = "releaseKey";
  public enum PortalPage {
    NOT_FOUND, TAXON, TOMBSTONE, DATASET, METADATA;
  }

  public enum Environment {PROD, PREVIEW, DEV};

  private final boolean requireCOL;
  private final SqlSessionFactory factory;
  private final DatasetDao datasetDao;
  private final DatasetSourceDao sourceDao;
  private final TaxonDao tdao;
  private final Map<Environment, Integer> releaseKeys = new HashMap<>();
  private final Map<Environment, Map<PortalPage, Template>> portalTemplates = Map.copyOf(
    Arrays.stream(Environment.values())
          .collect(Collectors.toMap(e -> e, e -> new HashMap<>()))
  );
  private Path portalTemplateDir = Path.of("/tmp");
  private final List<DatasetRelease> annualReleases;
  private final List<DatasetRelease> annualXReleases;

  public PortalPageRenderer(DatasetDao datasetDao, DatasetSourceDao sourceDao, TaxonDao tdao, Path portalTemplateDir, boolean requireCOL) throws IOException {
    this.requireCOL = requireCOL;
    this.datasetDao = datasetDao;
    this.sourceDao = sourceDao;
    this.factory = tdao.getFactory();
    this.tdao = tdao;
    this.portalTemplateDir = Preconditions.checkNotNull(portalTemplateDir);
    if (!Files.exists(portalTemplateDir)) {
      Files.createDirectories(portalTemplateDir);
    }
    loadReleases();
    loadTemplates();
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

  private static Response noReleaseDeployed(Environment env) {
    return Response
      .status(Response.Status.NOT_FOUND)
      .type(MediaType.TEXT_PLAIN)
      .entity(String.format("No COL release has been deployed to %s", env))
      .build();
  }

  /**
   * @param id a COL checklist taxon or synonym ID. In case of synonyms redirect to the taxon page.
   * @param env
   */
  public Response renderTaxon(String id, Environment env) throws TemplateException, IOException {
    if (!releaseKeys.containsKey(env)) return noReleaseDeployed(env);

    final int datasetKey = releaseKeys.get(env);
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

  public Response renderDatasource(int id, Environment env) throws TemplateException, IOException {
    if (!releaseKeys.containsKey(env)) return noReleaseDeployed(env);
    try {
      final int datasetKey = releaseKeys.get(env);
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
    if (!releaseKeys.containsKey(env)) return noReleaseDeployed(env);

    try {
      final int datasetKey = releaseKeys.get(env);
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
    if (temp == null) {
      return Response
        .status(Response.Status.NOT_FOUND)
        .type(MediaType.TEXT_PLAIN)
        .entity(String.format("No portal template has been deployed for %s/%s", env, pp))
        .build();
    }
    Writer out = new StringWriter();
    temp.process(data, out);

    return Response
      .status(pp == PortalPage.NOT_FOUND ? Response.Status.NOT_FOUND : Response.Status.OK)
      .type(MediaType.TEXT_HTML)
      .entity(out.toString())
      .build();
  }

  /**
   * Reads all release keys for all environments from the file system and stores them in the internal map
   * @throws IOException
   */
  private void loadReleases() throws IOException {
    for (Environment env : Environment.values()) {
      loadRelease(env);
    }
  }

  /**
   * Reads all portal templates for all environments keys from the file system and store it in the internal map
   * @throws IOException
   */
  private void loadTemplates() throws IOException {
    for (Environment env : Environment.values()) {
      if (!releaseKeys.containsKey(env)) {
        LOG.warn("No portal release deployed for environment {}", env);
        continue;
      }
      for (PortalPage pp : PortalPage.values()) {
        loadTemplate(env, pp);
      }
    }
  }

  private void loadRelease(Environment env) throws IOException {
    var p = releaseFile(env);
    if (Files.exists(p)) {
      var in = Files.newInputStream(p);
      var strKey = UTF8IoUtils.readString(in);
      int releaseKey = Integer.parseInt(strKey.trim());
      releaseKeys.put(env, releaseKey);
      LOG.info("Use release {} for environment {}", releaseKey, env);
    } else {
      LOG.warn("No release deployed for environment {}", env);
    }
  }

  private void loadTemplate(Environment env, PortalPage pp) throws IOException {
    var p = template(env, pp);
    if (Files.exists(p)) {
      LOG.info("Load {} {} portal template from {}", env, pp, p);
      try (BufferedReader br = UTF8IoUtils.readerFromPath(p)) {
        Template temp = new Template(pp.name(), br, FmUtil.FMK);
        portalTemplates.get(env).put(pp, temp);
      }
    } else {
      LOG.warn("{} {} portal template missing", env, pp);
    }
  }

  public Integer getReleaseKey(Environment env) {
    return releaseKeys.get(env);
  }

  public void setReleaseKey(Environment env, int releaseKey) throws IOException {
    if (requireCOL) {
      try {
        var info = DatasetInfoCache.CACHE.info(releaseKey, DatasetOrigin.RELEASE, DatasetOrigin.XRELEASE);
        if (info.sourceKey != Datasets.COL) {
          throw new IllegalArgumentException("Not a COL release key: " + info.sourceKey);
        }
      } catch (NotFoundException e) {
        throw new IllegalArgumentException(e.getMessage());
      }
    }
    LOG.info("Update environment {} to COL release {}", env, releaseKey);
    releaseKeys.put(env, releaseKey);
    try (Writer w = UTF8IoUtils.writerFromPath(releaseFile(env))) {
      w.write(String.valueOf(releaseKey));
    }
  }

  public boolean store(Environment env, PortalPage pp, String template) throws IOException {
    LOG.info("Store new portal page template {} for {} environment", pp, env);
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

  private Path releaseFile(Environment env) {
    return portalTemplateDir.resolve(Path.of(env.name(), RELEASE_KEY_FILE));
  }
}
