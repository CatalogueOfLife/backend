package org.col.img;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import org.apache.commons.io.FilenameUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.common.io.DownloadException;
import org.col.common.io.DownloadUtil;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogoUpdateJob implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(LogoUpdateJob.class);
  
  private final ImageService imgService;
  private final SqlSessionFactory factory;
  private final DownloadUtil downloader;
  private final Function<Integer, File> scratchDirFunc;
  private final Dataset dataset;
  
  /**
   * Pulls all dataset logos asynchroneously in a new thread.
   * @param scratchDirFunc function to return a scratch dir for a given datasetKey
   */
  public static void updateAllAsync(SqlSessionFactory factory, DownloadUtil downloader, Function<Integer, File> scratchDirFunc, ImageService imgService) {
    Thread thread = new Thread(new LogoUpdateJob(null, factory, downloader, scratchDirFunc, imgService), "logo-updater");
    thread.setDaemon(false);
    thread.start();
  }
  
  public static void updateDatasetAsync(Dataset d, SqlSessionFactory factory, DownloadUtil downloader, Function<Integer, File> scratchDirFunc, ImageService imgService) {
    if (d.getLogo() != null) {
      CompletableFuture.runAsync(
          new LogoUpdateJob(d, factory, downloader,scratchDirFunc, imgService)
      );
    }
  }
  
  /**
   * @param d if null pages through all datasets
   */
  private LogoUpdateJob(@Nullable Dataset d, SqlSessionFactory factory, DownloadUtil downloader, Function<Integer, File> scratchDirFunc, ImageService imgService) {
    this.dataset = d;
    this.imgService = imgService;
    this.factory = factory;
    this.downloader = downloader;
    this.scratchDirFunc = scratchDirFunc;
  }
  
  @Override
  public void run() {
    if (dataset != null) {
      pullLogo(dataset);
    } else {
      updateAll();
    }
  }
  
  private void updateAll() {
    Page page = new Page(0, 25);
    List<Dataset> datasets = null;
    int counter = 0;
    int failed = 0;
    while (datasets == null || !datasets.isEmpty()) {
      try (SqlSession session = factory.openSession()) {
        LOG.debug("Retrieving next dataset page for {}", page);
        datasets = session.getMapper(DatasetMapper.class).list(page);
        for (Dataset d : datasets) {
          Boolean result = pullLogo(d);
          if (result != null) {
            if (result) {
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
  private Boolean pullLogo(Dataset dataset) {
    if (dataset.getLogo() != null) {
      LOG.info("Pulling logo from {}", dataset.getLogo());
      String fn = FilenameUtils.getName(dataset.getLogo().getPath());
      if (Strings.isNullOrEmpty(fn)) {
        fn = "logo-original";
      }
      File logo = new File(scratchDirFunc.apply(dataset.getKey()), fn);
      try {
        downloader.download(dataset.getLogo(), logo);
        // now read image and copy to logo repo for resizing
        imgService.putDatasetLogo(dataset, ImageIO.read(logo));
        return true;
    
      } catch (DownloadException e) {
        LOG.error("Failed to download logo from {}", dataset.getLogo(), e);
    
      } catch (IOException e) {
        LOG.error("Failed to read logo image {} from downloaded file {}", dataset.getLogo(), logo.getAbsolutePath(), e);
      }
      return false;
    }
    return null;
  }
}
