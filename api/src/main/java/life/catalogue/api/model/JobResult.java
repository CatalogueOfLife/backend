package life.catalogue.api.model;

import life.catalogue.common.io.ChecksumUtils;
import life.catalogue.common.text.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;

public class JobResult extends DataEntity<UUID> {
  private static final Logger LOG = LoggerFactory.getLogger(JobResult.class);
  //TODO: not nice. Should better only be on JobConfig!
  private static URI DOWNLOAD_BASE_URI = URI.create("https://download.checklistbank.org/jobs/");
  private static File DOWNLOAD_DIR = new File("/tmp/jobs");

  /**
   * WARNING: Only set this when you know what you're doing!
   */
  public static void setDownloadConfigs(URI downloadBaseURI, File downloadDir) {
    if (!downloadBaseURI.getPath().endsWith("/")) {
      downloadBaseURI = URI.create(downloadBaseURI + "/");
    }
    LOG.warn("DownloadBaseURI changed from {} to {}", DOWNLOAD_BASE_URI, downloadBaseURI);
    DOWNLOAD_BASE_URI = downloadBaseURI;
    LOG.warn("downloadDir changed from {} to {}", DOWNLOAD_DIR, downloadDir);
    DOWNLOAD_DIR = downloadDir;
  }

  private UUID key;
  private String md5; // md5 for file
  private long size; // filesize in bytes

  public JobResult() {
  }

  public JobResult(UUID key) {
    this.key = key;
  }

  /**
   * @return the relative path to the download base URI that holds the download file.
   * @param key job key
   */
  public static String downloadFilePath(UUID key) {
    return downloadFilePath(key,"zip");
  }

  public static String downloadFilePath(UUID key, String suffix) {
    return key.toString().substring(0,2) + "/" + key + "." + suffix;
  }

  @Override
  public UUID getKey() {
    return key;
  }

  @Override
  public void setKey(UUID key) {
    this.key = key;
  }

  public URI getDownload() {
    return DOWNLOAD_BASE_URI.resolve(downloadFilePath(key));
  }

  public File getFile() {
    return new File(DOWNLOAD_DIR, downloadFilePath(key));
  }

  public String getMd5() {
    return md5;
  }

  public void setMd5(String md5) {
    this.md5 = md5;
  }

  public long getSize() {
    return size;
  }

  public String getSizeWithUnit() {
    return StringUtils.byteWithUnitSI(size);
  }

  public void setSize(long size) {
    this.size = size;
  }

  public void calculateSizeAndMd5() throws IOException {
    File f = getFile();
    this.size = Files.size(f.toPath());
    this.md5 = ChecksumUtils.getMD5Checksum(f);
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JobResult)) return false;
    if (!super.equals(o)) return false;
    JobResult that = (JobResult) o;
    return size == that.size
           && Objects.equals(key, that.key)
           && Objects.equals(md5, that.md5);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, md5, size);
  }
}
