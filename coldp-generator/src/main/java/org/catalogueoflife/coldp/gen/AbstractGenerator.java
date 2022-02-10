package org.catalogueoflife.coldp.gen;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.DOI;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.metadata.coldp.YamlMapper;
import life.catalogue.common.io.*;
import life.catalogue.common.text.SimpleTemplate;
import life.catalogue.metadata.coldp.DoiResolver;

import org.gbif.dwc.terms.Term;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGenerator implements Runnable {
  protected static Logger LOG = LoggerFactory.getLogger(AbstractGenerator.class);
  protected final GeneratorConfig cfg;
  protected final DownloadUtil download;
  private final boolean addMetadata;
  protected final Map<String, Object> metadata = new HashMap<>();
  protected final List<Citation> sources = new ArrayList<>();
  protected final String name;
  private final File dir; // working directory
  protected final File src; // optional download
  protected final URI srcUri;
  protected TermWriter writer;
  protected TermWriter refWriter;
  private int refCounter = 1;
  private final CloseableHttpClient hc;
  private final DoiResolver doiResolver;

  public AbstractGenerator(GeneratorConfig cfg, boolean addMetadata, @Nullable URI downloadUri) throws IOException {
    this.cfg = cfg;
    this.addMetadata = addMetadata;
    this.dir = cfg.archiveDir();
    dir.mkdirs();
    name = getClass().getPackageName().replaceFirst(AbstractGenerator.class.getPackageName(), "");
    src = new File("/tmp/" + name + ".src");
    src.deleteOnExit();
    HttpClientBuilder htb = HttpClientBuilder.create();
    hc = htb.build();
    this.download = new DownloadUtil(hc);
    this.srcUri = downloadUri;
    doiResolver = new DoiResolver();
  }


  @Override
  public void run() {
    try {
      // get latest CSVs
      if (!src.exists() && srcUri != null) {
        LOG.info("Downloading latest data from {}", srcUri);
        download.download(srcUri, src);
      } else if (srcUri == null) {
        LOG.warn("Reuse data from {}", src);
      }

      prepare();
      addData();
      if (writer != null) {
        writer.close();
      }
      if (refWriter != null) {
        refWriter.close();
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

  protected void prepare() throws IOException {
    //nothing by default, override as needed
  }

  /**
   * Finalizes the current ref record and creates a new ref id if not yet set.
   * @return the ID of the previous record.
   */
  protected String nextRef() throws IOException {
    String id;
    if (refWriter.has(ColdpTerm.ID)) {
      id = refWriter.get(ColdpTerm.ID);
    } else {
      id = "R" + refCounter++;
      refWriter.set(ColdpTerm.ID, id);
    }
    refWriter.next();
    return id;
  }

  protected void newWriter(ColdpTerm rowType) throws IOException {
    newWriter(rowType, ColdpTerm.RESOURCES.get(rowType));
  }

  protected void newWriter(Term rowType, List<? extends Term> columns) throws IOException {
    if (writer != null) {
      writer.close();
    }
    writer = additionalWriter(rowType, columns);
  }

  protected void initRefWriter(List<? extends Term> columns) throws IOException {
    refWriter = additionalWriter(ColdpTerm.Reference, columns);
  }

  protected TermWriter additionalWriter(ColdpTerm rowType) throws IOException {
    return new TermWriter.TSV(dir, rowType, ColdpTerm.RESOURCES.get(rowType));
  }

  protected TermWriter additionalWriter(Term rowType, List<? extends Term> columns) throws IOException {
    return new TermWriter.TSV(dir, rowType, columns);
  }

  protected abstract void addData() throws Exception;

  protected void addMetadata() throws Exception {
    if (addMetadata) {
      // do we have sources?
      StringBuilder yaml = new StringBuilder();
      if (!sources.isEmpty()) {
        yaml.append("source: \n");
        for (Citation src : sources) {
          yaml.append(" - \n");
          String citation = YamlMapper.MAPPER.writeValueAsString(src);
          String indented = new BufferedReader(new StringReader(citation)).lines()
                                                        .map(l -> "   " + l)
                                                        .collect(Collectors.joining("\n"));
          yaml.append(indented);
          yaml.append("\n");
        }
      }
      metadata.put("sources", yaml.toString());

      // use metadata to format
      String template = UTF8IoUtils.readString(Resources.stream(cfg.source+"/metadata.yaml"));
      try (var mw = UTF8IoUtils.writerFromFile(new File(dir, "metadata.yaml"))) {
        mw.write(SimpleTemplate.render(template, metadata));
      }
    }
  }

  /**
   * Adds a new source entry to the metadata map by resolving a DOI.
   * @param doi
   */
  protected void addSource(DOI doi) throws IOException {
    var data = doiResolver.resolve(doi);
    sources.add(data);
  }
}
