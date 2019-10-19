package org.col.importer.yaml;

import java.io.*;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.api.jackson.TermSerde;
import org.col.api.vocab.DataFormat;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedArchiveService {
  private static final Logger LOG = LoggerFactory.getLogger(DistributedArchiveService.class);
  private static final ObjectReader DESCRIPTOR_READER;
  static {
    ObjectMapper OM = new ObjectMapper(new YAMLFactory())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new YamlModule());
    DESCRIPTOR_READER = OM.readerFor(ArchiveDescriptor.class);
  }
  private static class YamlModule extends SimpleModule {
    public YamlModule() {
      super("Yaml");
      addDeserializer(Term.class, new TermSerde.Deserializer());
      addSerializer(Term.class, new TermSerde.ValueSerializer());
    }
  }
  
  private final CloseableHttpClient client;
  
  public DistributedArchiveService(CloseableHttpClient hc) {
    this.client = hc;
  }
  
  public DataFormat download(URI url, File archiveFile) throws IOException {
    return download(read(url.toURL().openStream()), archiveFile);
  }
  
  public DataFormat uploaded(File archiveFile) throws IOException {
    return download(read(new FileInputStream(archiveFile)), archiveFile);
  }
  
  private ArchiveDescriptor read(InputStream is) throws IOException {
    return DESCRIPTOR_READER.readValue(is);
  }
  
  /**
   * Takes descriptor and downloads each file into a zipped archive
   * @param ad descriptor
   * @param archiveFile file to zip into
   * @return true format of the data files
   */
  private DataFormat download(ArchiveDescriptor ad, File archiveFile) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(archiveFile);
         ZipOutputStream zipOut = new ZipOutputStream(fos)
    ) {
      for (ArchiveDescriptor.FileDescriptor fd : ad.files) {
        LOG.info("Copy {} into {}", fd.url, fd.name);
        HttpGet get = new HttpGet(fd.url);
        try (CloseableHttpResponse resp = client.execute(get)) {
          if (resp.getEntity() == null) {
            LOG.error("Could not get remote resource {}: {}", fd.url, resp.getStatusLine());
            continue;
          }
          ZipEntry zipEntry = new ZipEntry(fd.name);
          zipOut.putNextEntry(zipEntry);
          IOUtils.copy(new ReplacedHeaderStream(resp.getEntity().getContent(), fd), zipOut);
        }
      }
    }
    return ad.format;
  }
  
  static class ReplacedHeaderStream extends InputStream {
    private final ArchiveDescriptor.FileDescriptor fd;
    private final InputStream original;
    private byte[] header;
    private int idx = 0;
  
    ReplacedHeaderStream(InputStream original, ArchiveDescriptor.FileDescriptor fd) throws IOException {
      this.original = original;
      this.fd = fd;
      fetchHeader();
    }
  
    private void fetchHeader() throws IOException {
      if (fd.header != null && !fd.header.isEmpty()) {
        // skip original header up to first newline
        int intch;
        while ((intch = original.read()) != -1) {
          if (intch == '\n') break;
        }
        // create new header as byte array
        StringBuilder sb = new StringBuilder();
        for (Term t : fd.header) {
          if (sb.length() > 1) {
            sb.append(fd.delimiter);
            if (fd.quotation != null) {
              sb.append(fd.quotation);
            }
          } else if (fd.quotation != null) {
            sb.append(fd.quotation);
          }
          sb.append(t.prefixedName());
          if (fd.quotation != null) {
            sb.append(fd.quotation);
          }
        }
        sb.append("\n");
        // convert header string to array
        header = sb.toString().getBytes(fd.encoding);
      }
    }
    
    @Override
    public int read() throws IOException {
      if (header != null && idx < header.length) {
        return header[idx++];
      } else {
        return original.read();
      }
    }
  }
}
