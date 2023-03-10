package life.catalogue.matching;

import com.fasterxml.jackson.core.type.TypeReference;

import life.catalogue.WsServerConfig;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.JobResult;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NamesIndexMapper;

import java.io.File;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.univocity.parsers.common.AbstractWriter;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;

public class NidxExportJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(NidxExportJob.class);
  private static final TypeReference<Map<Integer, String>> TYPE_REF = new TypeReference<Map<Integer, String>>() {};
  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;
  // job specifics
  private final List<Integer> datasetKeys;
  private final List<Dataset> datasets;
  private final JobResult result;
  private int counter = 0;

  public NidxExportJob(List<Integer> datasetKeys, int userKey, SqlSessionFactory factory, WsServerConfig cfg) {
    super(JobPriority.LOW, userKey);
    this.cfg = cfg;
    this.datasetKeys = List.copyOf(datasetKeys);
    List<Dataset> datasets = new ArrayList<>();
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      for (var key : datasetKeys) {
        datasets.add(dm.get(key));
      }
    }
    this.datasets = List.copyOf(datasets);
    this.factory = factory;
    this.result = new JobResult(getKey());
  }

  public List<Dataset> getDatasets() {
    return datasets;
  }

  public JobResult getResult() {
    return result;
  }

  private File matchResultFile() {
    return cfg.normalizer.jobResultFile(getKey());
  }

  @Override
  public String getEmailTemplatePrefix() {
    return "nidx-export";
  }

  @Override
  public void execute() throws Exception {
    try (TempFile tmp = new TempFile(matchResultFile());
         Writer fw = UTF8IoUtils.writerFromGzipFile(tmp.file)
    ) {
      LOG.info("Write nidx export for job {} to temp file {}", getKey(), tmp.file.getAbsolutePath());
      AbstractWriter<?> writer = new TsvWriter(fw, new TsvWriterSettings());
      List<String> headers = new ArrayList<>();
      headers.add("rank");
      headers.add("scientificName");
      headers.add("authorship");
      for (Integer key : datasetKeys) {
        headers.add("dataset"+key+"-ID");
      }
      writer.writeHeaders(headers);

      try (SqlSession session = factory.openSession()) {
        var nim = session.getMapper(NamesIndexMapper.class);
        try (var cursor = nim.processDatasets(datasetKeys)) {
          for (var sn : cursor) {
            counter++;
            var row = new String[datasetKeys.size()+3];
            row[0] = str(sn.getRank());
            row[1] = sn.getName();
            row[2] = sn.getAuthorship();
            var map = ApiModule.MAPPER.readValue(sn.getId(), TYPE_REF);
            int idx = 3;
            for (Integer dk : datasetKeys) {
              String value = map.get(dk);
              row[idx++] = value;
            }
            writer.writeRow(row);
          }
        }

      } finally {
        writer.close();
      }

      // move to final result file
      FileUtils.copyFile(tmp.file, result.getFile());
      result.calculateSizeAndMd5();
      LOG.info("Nidx export {} with {} names from datasets {} completed: {} [{}]", getKey(), counter, datasetKeys.stream().map(Object::toString).collect(Collectors.joining(", ")), result.getFile(), result.getSizeWithUnit());
    }
  }

  public int getCount() {
    return counter;
  }

  static String str(Enum<?> val) {
    return val == null ? null : PermissiveEnumSerde.enumValueName(val);
  }

}
