package org.catalogueoflife.coldp.gen;

import com.google.common.io.MoreFiles;

import com.google.common.io.RecursiveDeleteOption;

import life.catalogue.common.io.*;
import life.catalogue.common.text.SimpleTemplate;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGenerator implements Runnable {
  protected static Logger LOG = LoggerFactory.getLogger(AbstractGenerator.class);
  protected final GeneratorConfig cfg;
  protected final DownloadUtil download;
  private final boolean addMetadata;
  protected final Map<String, Object> metadata = new HashMap<>();
  private final File dir; // working directory
  protected TermWriter writer;
  protected TermWriter refWriter;
  private final CloseableHttpClient hc;

  public AbstractGenerator(GeneratorConfig cfg, boolean addMetadata) {
    this.cfg = cfg;
    this.addMetadata = addMetadata;
    this.dir = cfg.archiveDir();
    dir.mkdirs();
    HttpClientBuilder htb = HttpClientBuilder.create();
    hc = htb.build();
    download = new DownloadUtil(hc);
  }


  @Override
  public void run() {
    try {
      addData();
      if (writer != null) {
        writer.close();
      }
      addMetadata();

      // finish archive and zip it
      LOG.info("Bundling archive at {}", dir.getAbsolutePath());
      File zip = new File(dir.getParentFile(), dir.getName() + ".zip");
      CompressionUtil.zipDir(dir, zip);
      LOG.info("ColDP archive completed at {} !", zip);

    } catch (Exception e) {
      LOG.error("Error building ColDP archive for {}", cfg.source, e);
      throw new RuntimeException(e);
    } finally {
      try {
        hc.close();
      } catch (IOException e) {
        LOG.error("Failed to close http client", e);
        throw new RuntimeException(e);
      }
    }
  }

  protected void newWriter(Term rowType, List<Term> columns) throws IOException {
    if (writer != null) {
      writer.close();
    }
    writer = additionalWriter(rowType, columns);
  }

  protected TermWriter additionalWriter(Term rowType, List<Term> columns) throws IOException {
    return new TermWriter.TSV(dir, rowType, columns);
  }

  protected abstract void addData() throws Exception;

  protected void addMetadata() throws Exception {
    if (addMetadata) {
      // use metadata to format
      String template = UTF8IoUtils.readString(Resources.stream(cfg.source+"/metadata.yaml"));
      try (var mw = UTF8IoUtils.writerFromFile(new File(dir, "metadata.yaml"))) {
        mw.write(SimpleTemplate.render(template, metadata));
      }
    }
  }

}
