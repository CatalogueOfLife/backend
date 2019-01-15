package org.col.admin.logoupdater;

import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

import com.google.common.base.Strings;
import org.apache.commons.io.FilenameUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.NormalizerConfig;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.common.io.DownloadException;
import org.col.common.io.DownloadUtil;
import org.col.db.mapper.DatasetMapper;
import org.col.img.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogoUpdateJob implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(LogoUpdateJob.class);
  
  private final ImageService imgService;
  private final SqlSessionFactory factory;
  private final DownloadUtil downloader;
  private final NormalizerConfig cfg;
  
  public static void updateAll(SqlSessionFactory factory, DownloadUtil downloader, NormalizerConfig cfg, ImageService imgService) {
    Thread thread = new Thread(new LogoUpdateJob(factory, downloader, cfg, imgService), "logo-updater");
    thread.setDaemon(false);
    thread.start();
  }
  
  public LogoUpdateJob(SqlSessionFactory factory, DownloadUtil downloader, NormalizerConfig cfg, ImageService imgService) {
    this.imgService = imgService;
    this.factory = factory;
    this.downloader = downloader;
    this.cfg = cfg;
  }
  
  @Override
  public void run() {
    Page page = new Page(0, 25);
    List<Dataset> datasets = null;
    int counter = 0;
    int failed = 0;
    while (datasets == null || !datasets.isEmpty()) {
      try (SqlSession session = factory.openSession()) {
        LOG.debug("Retrieving next dataset page for {}", page);
        datasets = session.getMapper(DatasetMapper.class).list(page);
        for (Dataset d : datasets) {
          LOG.debug("dataset {}", d.getTitle());
          if (d.getLogo() != null) {
            if (LogoUpdateJob.pullLogo(d, downloader, cfg, imgService)) {
              counter++;
            } else {
              failed++;
            }
          }
        }
        page.next();
      }
    }
    LOG.info("Pulled {} external logos, failed {}", counter, failed);
  }
  
  /**
   * @return true if a logo was successfully pulled from the source
   */
  public static boolean pullLogo(Dataset dataset, DownloadUtil downloader, NormalizerConfig cfg, ImageService imgService) {
    LOG.info("Pulling logo from {}", dataset.getLogo());
    String fn = FilenameUtils.getName(dataset.getLogo().getPath());
    if (Strings.isNullOrEmpty(fn)) {
      fn = "logo-original";
    }
    File logo = new File(cfg.scratchDir(dataset.getKey()), fn);
    try {
      downloader.download(dataset.getLogo(), logo);
      // now read image and copyTaxon to logo repo for resizing
      imgService.putDatasetLogo(dataset, ImageIO.read(logo));
      return true;
      
    } catch (DownloadException e) {
      LOG.error("Failed to download logo from {}", dataset.getLogo(), e);
      
    } catch (IOException e) {
      LOG.error("Failed to read logo image {} from downloaded file {}", dataset.getLogo(), logo.getAbsolutePath(), e);
    }
    return false;
  }
}
