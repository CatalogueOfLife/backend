package org.col.img;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import com.google.common.base.Strings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Dataset;
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
  private final BiFunction<Integer, String, File> scratchFileFunc;
  private final Dataset dataset;
  
  
  /**
   * Creates a new job pulling all dataset logos asynchroneously.
   * @param scratchFileFunc function to return a scratch dir for a given datasetKey
   * @return new thread ready to be started
   */
  public static LogoUpdateJob updateAllAsync(SqlSessionFactory factory, DownloadUtil downloader, BiFunction<Integer, String, File> scratchFileFunc, ImageService imgService) {
    return new LogoUpdateJob(null, factory, downloader, scratchFileFunc, imgService);
  }
  
  public static void updateDatasetAsync(Dataset d, SqlSessionFactory factory, DownloadUtil downloader, BiFunction<Integer, String, File> scratchFileFunc, ImageService imgService) {
    if (d.getLogo() != null) {
      CompletableFuture.runAsync(
          new LogoUpdateJob(d, factory, downloader,scratchFileFunc, imgService)
      );
    }
  }
  
  /**
   * @param d if null pages through all datasets
   */
  private LogoUpdateJob(@Nullable Dataset d, SqlSessionFactory factory, DownloadUtil downloader, BiFunction<Integer, String, File> scratchFileFunc, ImageService imgService) {
    this.dataset = d;
    this.imgService = imgService;
    this.factory = factory;
    this.downloader = downloader;
    this.scratchFileFunc = scratchFileFunc;
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
    AtomicInteger counter = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    try (SqlSession session = factory.openSession()) {
      LOG.info("Update all logos");
      session.getMapper(DatasetMapper.class).process("logo IS NOT NULL", null, new ResultHandler<Dataset>() {
        @Override
        public void handleResult(ResultContext<? extends Dataset> ctx) {
          Boolean result = pullLogo(ctx.getResultObject());
          if (result != null) {
            if (result) {
              counter.incrementAndGet();
            } else {
              failed.incrementAndGet();
            }
          }
        }
      });
      LOG.info("Finished pulling all logos");
      
    } catch (Exception e) {
      LOG.error("Error updating all logos", e);
      throw e;
      
    } finally {
      LOG.info("Pulled {} external logos, failed {}", counter, failed);
    }
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
      File logo = scratchFileFunc.apply(dataset.getKey(), fn);
      try {
        downloader.download(dataset.getLogo(), logo);
        // now read image and copy to logo repo for resizing
        imgService.putDatasetLogo(dataset.getKey(), ImageIO.read(logo));
        return true;
    
      } catch (DownloadException e) {
        LOG.error("Failed to download logo from {}", dataset.getLogo(), e);
    
      } catch (IOException e) {
        LOG.error("Failed to read logo image {} from downloaded file {}", dataset.getLogo(), logo.getAbsolutePath(), e);

      } finally {
        FileUtils.deleteQuietly(logo);
      }
      return false;
    }
    return null;
  }
}
