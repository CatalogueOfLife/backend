package org.col.admin.task.importer;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.col.util.io.CompressionUtil;
import org.col.util.io.DownloadUtil;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 *
 */
public class ExternalSourceUtil {

  /**
   * Download and extracts an external source archive into a temp folder that gets removed after the consumer is finished.
   * @param url externals source location
   * @param sourceConsumer the consumer for the source
   * @throws Exception
   */
  public static void consumeSource(URI url, Consumer<Path> sourceConsumer) throws Exception {
    // download file
    HttpClientBuilder htb = HttpClientBuilder.create();
    File tmp = File.createTempFile("col-gsd", ".tar.gz");
    Path source = java.nio.file.Files.createTempDirectory("col-gsd");
    try (CloseableHttpClient hc = htb.build()) {
      DownloadUtil down = new DownloadUtil(hc);
      down.downloadIfModified(url, tmp);
      // decompress into folder
      CompressionUtil.decompressFile(source.toFile(), tmp);

      // consume source folder before its being removed again
      sourceConsumer.accept(source);

    } finally {
      FileUtils.deleteQuietly(tmp);
      MoreFiles.deleteRecursively(source, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }}
