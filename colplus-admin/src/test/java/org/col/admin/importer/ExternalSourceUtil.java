package org.col.admin.importer;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.google.common.base.Strings;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.col.common.io.CompressionUtil;
import org.col.common.io.DownloadUtil;

/**
 *
 */
public class ExternalSourceUtil {
  
  /**
   * Download and extracts an external source archive into a temp folder that gets removed after the consumer is finished.
   *
   * @param url            externals source location
   * @param sourceConsumer the consumer for the source
   * @throws Exception
   */
  public static void consumeSource(URI url, Consumer<Path> sourceConsumer) throws Exception {
    // download file
    HttpClientBuilder htb = HttpClientBuilder.create();
    String suffix = FilenameUtils.getExtension(url.getPath());
    if (Strings.isNullOrEmpty(suffix)) {
      suffix = "tar.gz";
    }
    File tmp = File.createTempFile("col-gsd", "." + suffix);
    Path source = java.nio.file.Files.createTempDirectory("col-gsd");
    try (CloseableHttpClient hc = htb.build()) {
      DownloadUtil down = new DownloadUtil(hc);
      down.download(url, tmp);
      // decompress into folder
      CompressionUtil.decompressFile(source.toFile(), tmp);
      
      // consume source folder before its being removed again
      sourceConsumer.accept(source);
      
    } finally {
      FileUtils.deleteQuietly(tmp);
      MoreFiles.deleteRecursively(source, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }
  
  /**
   * Extracts a local source archive into a temp folder that gets removed after the consumer is finished.
   *
   * @param file           local source location
   * @param sourceConsumer the consumer for the source
   * @throws Exception
   */
  public static void consumeFile(File file, Consumer<Path> sourceConsumer) throws Exception {
    // download file
    Path source = java.nio.file.Files.createTempDirectory("col-gsd");
    try {
      // decompress into folder
      CompressionUtil.decompressFile(source.toFile(), file);
      // consume source folder before its being removed again
      sourceConsumer.accept(source);
      
    } finally {
      MoreFiles.deleteRecursively(source, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }
}
