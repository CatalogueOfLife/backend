package life.catalogue.command;

import com.codahale.metrics.MetricRegistry;
import com.google.common.eventbus.EventBus;

import io.dropwizard.client.JerseyClientBuilder;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import life.catalogue.doi.service.DataCiteService;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.dw.mail.MailBundle;

import life.catalogue.exporter.ExportManager;

import life.catalogue.img.ImageService;

import life.catalogue.img.ImageServiceFS;

import life.catalogue.release.PublicReleaseListener;

import org.apache.ibatis.session.SqlSession;

import net.sourceforge.argparse4j.inf.Subparser;

import javax.validation.Validation;
import javax.validation.Validator;

/**
 * Command that exports a single dataset or all its releases if it is a project.
 */
public class ExportCmd extends AbstractMybatisCmd {
  private static final String ARG_KEY = "key";
  private static final String ARG_PRIVATE = "private";
  private static final String ARG_FORCE = "force";
  private static final String ARG_FORMAT = "format";

  private JobExecutor exec;
  private ExportManager manager;
  private final MailBundle mail = new MailBundle();
  private PublicReleaseListener copy;
  private Set<DataFormat> formats;
  private Set<UUID> exports;
  private boolean force;

  public ExportCmd() {
    super("export", true, "Export a single dataset or all its releases if it is a project.");
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("--"+ARG_KEY, "-k")
        .dest(ARG_KEY)
        .type(Integer.class)
        .required(true)
        .help("dataset key of the release or project to export");
    subparser.addArgument("--"+ARG_PRIVATE)
      .dest(ARG_PRIVATE)
      .type(Boolean.class)
      .setDefault(false)
      .required(false)
      .help("flag to also include private releases for exported projects");
    subparser.addArgument("--"+ARG_FORCE)
      .dest(ARG_FORCE)
      .type(Boolean.class)
      .setDefault(false)
      .required(false)
      .help("flag to force a new export even if it exists already");
    subparser.addArgument("--"+ARG_FORMAT)
      .dest(ARG_FORMAT)
      .type(DataFormat.class)
      .required(false)
      .help("Export format to use. Defaults to all");
  }

  @Override
  void execute() throws Exception {
    DataFormat df = ns.get(ARG_FORMAT);
    if (df != null) {
      formats = Set.of(df);
    } else {
      formats = Arrays.stream(DataFormat.values()).filter(DataFormat::isExportable).collect(Collectors.toSet());
    }
    System.out.printf("Export format(s): %s\n", formats.stream().map(DataFormat::getName).collect(Collectors.joining(", ")));

    force = ns.getBoolean(ARG_FORCE);
    if (force) {
      System.out.printf("Enforce new exports\n");
    }

    Dataset d;
    List<Dataset> datasets;
    try (SqlSession session = factory.openSession()){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      d = dm.get(ns.getInt(ARG_KEY));
      if (d == null) {
        throw NotFoundException.notFound(Dataset.class, ns.getInt(ARG_KEY));
      }

      EventBus bus = new EventBus();
      Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      mail.run(cfg, null);
      exec = new JobExecutor(cfg.job, mail.getMailer());
      final ImageService imageService = new ImageServiceFS(cfg.img);
      final DatasetExportDao exportDao = new DatasetExportDao(cfg.exportDir, factory, bus, validator);
      manager = new ExportManager(cfg, factory, exec, imageService, mail.getMailer(), exportDao, new DatasetImportDao(factory, cfg.metricsRepo));
      UserDao udao = new UserDao(factory, bus, validator);
      DoiService doiService = new DataCiteService(cfg.doi, jerseyClient);
      DatasetConverter converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);
      copy = new PublicReleaseListener(cfg, factory, exportDao, doiService, converter);

      if (d.getOrigin() == DatasetOrigin.MANAGED) {
        boolean inclPrivate = ns.getBoolean(ARG_PRIVATE);
        DatasetSearchRequest req = new DatasetSearchRequest();
        req.setReleasedFrom(d.getKey());
        req.setPrivat(inclPrivate);
        datasets = dm.search(req, userKey, new Page(0, 1000));

        System.out.printf("Exporting %s releases of project %s\n", datasets.size(), d.getKey());
        for (var rel : datasets) {
          export(rel);
        }

      } else {
        export(d);
        datasets = List.of(d);
      }

    } finally {
      // wait for exports to finish
      while (!exec.isIdle()) {
        TimeUnit.SECONDS.sleep(10);
      }
      // give datacite API some time
      TimeUnit.SECONDS.sleep(60);
      System.out.println("Shutting down executor");
      exec.close();
    }

    // move exports to COL download dir?
    if (d.getKey() == Datasets.COL || Objects.equals(Datasets.COL, d.getSourceKey())) {
      for (Dataset de : datasets) {
        copy.copyExportsToColDownload(de, false);
      }
    }
  }

  void export(Dataset d) {
    System.out.printf("Export %s %s from %s\n", d.getOrigin(), d.getKey(), d.getIssued());

    ExportRequest req = new ExportRequest();
    req.setForce(force);
    for (DataFormat df : formats) {
      req.setFormat(df);
      UUID key = manager.submit(req, userKey);
      exports.add(key);
      System.out.printf("  scheduled %s export %s\n", df, key);
    }
  }

}
