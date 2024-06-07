package life.catalogue.matching;

import com.github.dockerjava.api.command.CreateContainerResponse;

import com.github.dockerjava.api.command.InspectImageResponse;

import com.github.dockerjava.api.command.PullImageResultCallback;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.printer.DwcaPrinter;
import life.catalogue.printer.PrinterFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Volume;

/**
 * Jonathan Rees Listtool integration
 * https://github.com/jar398/listtools
 * https://github.com/gbif/gbif-docker-images/blob/feature/checklist-image/checklist-tools/Dockerfile
 */
public class TaxonomicAlignJob extends BackgroundJob {
  private static final String IMAGE = "docker.gbif.org/clb-listtools";
  private static final String VERSION = "0.0.1";
  private static final String IMAGE_VERSION = IMAGE +":"+ VERSION;
  private static final Logger LOG = LoggerFactory.getLogger(TaxonomicAlignJob.class);
  private final int datasetKey1;
  private final String root1;
  private final int datasetKey2;
  private final String root2;

  private final Dataset dataset;
  private SimpleName taxon;
  private final Dataset dataset2;
  private SimpleName taxon2;
  private final JobResult result;
  private final SqlSessionFactory factory;
  private final DockerClient client;
  private final File tmpDir;
  private final File src;
  protected final File src1;
  protected final File src2;


  public TaxonomicAlignJob(int userKey, int datasetKey1, String root1, int datasetKey2, String root2, SqlSessionFactory factory, DockerClient client, NormalizerConfig cfg) throws IOException {
    super(userKey);
    this.client = client;
    this.factory = factory;
    this.datasetKey1 = datasetKey1;
    this.root1 = root1;
    this.datasetKey2 = datasetKey2;
    this.root2 = root2;

    // load dataset & taxon metadata
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var num = session.getMapper(NameUsageMapper.class);
      dataset = dm.getOrThrow(datasetKey1, Dataset.class);
      dataset2 = dm.getOrThrow(datasetKey2, Dataset.class);
      if (root1 != null) {
        var key = DSID.of(datasetKey1, root1);
        taxon = num.getSimple(key);
        if (taxon == null) throw NotFoundException.notFound(Taxon.class, key);
      }
      if (root2 != null) {
        var key = DSID.of(datasetKey2, root2);
        taxon2 = num.getSimple(key);
        if (taxon2 == null) throw NotFoundException.notFound(Taxon.class, key);
      }
    }
    this.result = new JobResult(getKey());
    this.tmpDir = cfg.scratchDir(getKey());
    this.src = new File(tmpDir, "sources");
    this.src1 = new File(src, "a");
    this.src2 = new File(src, "b");
    FileUtils.forceMkdir(src1);
    FileUtils.forceMkdir(src2);
  }

  protected void copyData() throws IOException {
    copyData(src1, datasetKey1, root1);
    copyData(src2, datasetKey2, root2);
  }

  private void copyData(File dir, int datasetKey, String taxonID) throws IOException {
    File f = new File(dir, "Taxon.tsv");
    try (Writer writer = UTF8IoUtils.writerFromFile(f)) {
      var ttp = TreeTraversalParameter.dataset(datasetKey, taxonID);
      var printer = PrinterFactory.dataset(DwcaPrinter.TSV.class, ttp, null, null, null, null, factory, writer);
      int cnt = printer.print();
      LOG.info("Written {} taxa to {} for dataset {}", cnt, f.getAbsolutePath(), datasetKey);
    }
  }

  @Override
  public void execute() throws Exception {
    LOG.info("Aligning all taxa from dataset {} [{}] and {} [{}]", datasetKey1, taxon, datasetKey2, taxon2);

    LOG.info("Export data");
    copyData();

    var container = buildContainer();
    try {
      LOG.info("Starting container {} and align names using listtools", container.getId());
      client.startContainerCmd(container.getId()).exec();

      var callback = client.waitContainerCmd(container.getId()).start();
      callback.awaitCompletion();

      dumpDockerLogs(client, container.getId(), new File(tmpDir, "align.log"));

    } catch (Exception e) {
      LOG.error("Error running taxonomic alignment job {}", getKey(), e);
      throw e;

    } finally {
      LOG.info("Removing container {}", container.getId());
      client.removeContainerCmd(container.getId()).exec();
    }
  }

  private CreateContainerResponse buildContainer() throws InterruptedException {
    // do we need to pull the image from the registry?
    try {
      client.inspectImageCmd(IMAGE_VERSION).exec();
    } catch (com.github.dockerjava.api.exception.NotFoundException e) {
      LOG.info("Pulling docker image {} from registry", IMAGE_VERSION);
      client.pullImageCmd(IMAGE)
        .withTag(VERSION)
        .exec(new PullImageResultCallback())
        .awaitCompletion(30, TimeUnit.SECONDS);
    }
    final String dname = "job-" + getKey();
    LOG.info("Create docker container {} from image {}", dname, IMAGE_VERSION);
    return client.createContainerCmd(IMAGE_VERSION)
      .withCmd("./execute.sh")
      .withName(dname)
      .withAttachStdout(true)
      .withAttachStderr(true)
      .withTty(true)
      .withBinds(
        new Bind(src.getAbsolutePath(), new Volume("/home/gbif/source")),
        new Bind(tmpDir.getAbsolutePath(), new Volume("/home/gbif/work"))
      )
      .exec();
  }

  static class LogContainerResultCallback extends ResultCallbackTemplate<LogContainerResultCallback, Frame> implements AutoCloseable {
    private final Writer log;

    public LogContainerResultCallback(File logfile) throws IOException {
      this.log = UTF8IoUtils.writerFromFile(logfile);
    }

    public void onNext(Frame item) {
      try {
        log.append(new String(item.getPayload()).trim());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() throws IOException {
      super.close();
      log.close();
    }
  }

  private void dumpDockerLogs(DockerClient docker, String containerId, File logfile) throws InterruptedException, IOException {
    LogContainerCmd logContainerCmd = docker.logContainerCmd(containerId);
    logContainerCmd.withStdOut(true).withStdErr(true);

    var callback = new LogContainerResultCallback(logfile);
    try {
      logContainerCmd.exec(callback).awaitCompletion();
    } finally {
      callback.close();
    }
  }

  @Override
  protected void onFinish() throws Exception {
    // copy & zip files
    LOG.info("Bundling alignment from {} at {}", tmpDir.getAbsolutePath(), result.getFile().getAbsolutePath());
    FileUtils.forceMkdir(result.getFile().getParentFile());
    CompressionUtil.zipDir(tmpDir, result.getFile(), false);
    result.calculateSizeAndMd5();
    // remove tmp files
    FileUtils.deleteQuietly(tmpDir);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof TaxonomicAlignJob) {
      var oth = (TaxonomicAlignJob) other;
      return datasetKey1 == oth.datasetKey1 &&
        datasetKey2 == oth.datasetKey2 &&
        Objects.equals(root1, oth.root1) &&
        Objects.equals(root2, oth.root2);
    }
    return super.isDuplicate(other);
  }

  @Override
  public String getEmailTemplatePrefix() {
    return "taxalign";
  }

  public JobResult getResult() {
    return result;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public SimpleName getTaxon() {
    return taxon;
  }

  public Dataset getDataset2() {
    return dataset2;
  }

  public SimpleName getTaxon2() {
    return taxon2;
  }
}
