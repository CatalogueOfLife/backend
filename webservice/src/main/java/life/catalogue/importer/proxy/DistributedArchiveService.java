package life.catalogue.importer.proxy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import life.catalogue.api.jackson.TermSerde;
import life.catalogue.common.io.PathUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
  
  public ArchiveDescriptor download(URI url, File archiveFile) throws IOException {
    return download(read(url.toURL().openStream()), archiveFile);
  }
  
  public ArchiveDescriptor uploaded(File archiveFile) throws IOException {
    return download(read(new FileInputStream(archiveFile)), archiveFile);
  }

  public static ArchiveDescriptor read(InputStream is) throws IllegalArgumentException {
    try {
      return DESCRIPTOR_READER.readValue(is);
    } catch (IOException e) {
      LOG.error("Failed to read proxy archive descriptor", e);
      throw new IllegalArgumentException("Failed to read proxy archive descriptor: "+e.getMessage());
    }
  }

  public static boolean isReadable(InputStream is) {
    try {
      ArchiveDescriptor desc = DESCRIPTOR_READER.readValue(is);
      return desc != null && desc.format != null && !desc.files.isEmpty();
    } catch (Exception e) {
    }
    return false;
  }

  public static Optional<Path> isReadable(Path folder) {
    try {
      for (Path f : PathUtils.listFiles(folder, Set.of("yaml", "yml", "archive"))) {
        if (isReadable(Files.newInputStream(f))) {
          LOG.info("Found readable proxy descriptor {}", f);
          return Optional.of(f);
        }
      }
    } catch (Exception e) {
      LOG.warn("Error trying to find readable yaml proxy descriptor in folder {}", folder, e);
    }
    return Optional.empty();
  }


  /**
   * Takes descriptor and downloads each file into a zipped archive
   * @param ad descriptor
   * @param archiveFile file to zip into
   * @return true format of the data files
   */
  private ArchiveDescriptor download(ArchiveDescriptor ad, File archiveFile) throws IOException {
    LOG.info("Proxy archive descriptor found with {} {} files", ad.files.size(), ad.format);
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
    } catch (IOException e) {
      LOG.error("Error creating proxied {} archive at {}", ad.format, archiveFile, e);
      throw e;
    }
    return ad;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte b;
        while ((b = (byte) original.read()) != -1) {
          if (b == '\n' || b == '\r') break;
          baos.write(new byte[]{b}, 0, 1);
        }
        deriveCsvFormat(baos.toString(fd.encoding));
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
  
    private void deriveCsvFormat(String line) {
      if (fd.delimiter == null) {
        int cols = fd.header.size();
        for (char delim : new char[]{',', '\t', ';', '|'}) {
          int cnt = StringUtils.countMatches(line, delim);
          // cols are columns, cnt are number of delimiters which should be one less
          if (cnt+1 == cols) {
            fd.delimiter = String.valueOf(delim);
            break;
          }
        }
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
