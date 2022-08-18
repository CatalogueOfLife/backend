package life.catalogue.img;

import life.catalogue.api.exception.NotFoundException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageServiceFS implements ImageService {
  public static final String IMAGE_FORMAT = "png";
  private static final Logger LOG = LoggerFactory.getLogger(ImageServiceFS.class);
  
  private final ImgConfig cfg;
  
  public ImageServiceFS(ImgConfig cfg) {
    this.cfg = cfg;
  }
  
  public static BufferedImage read(InputStream img) throws IOException {
    if (img == null) {
      throw new IllegalArgumentException("No image content");
    }
    BufferedImage bi = ImageIO.read(img);
    if (bi == null) {
      throw new UnsupportedFormatException("Image format not supported");
    }
    return bi;
  }

  @Override
  public boolean delete(int datasetKey) {
    boolean deleted = false;
    for (ImgConfig.Scale scale : ImgConfig.Scale.values()) {
      deleted = FileUtils.deleteQuietly(cfg.datasetLogo(datasetKey, scale).toFile()) || deleted;
    }
    return deleted;
  }

  @Override
  public boolean datasetLogoExists(int datasetKey) {
    return Files.exists(cfg.datasetLogo(datasetKey, ImgConfig.Scale.ORIGINAL));
  }

  @Override
  public void putDatasetLogo(int datasetKey, BufferedImage img) throws IOException {
    LOG.info("{} logo for dataset {}", img == null ? "Delete" : "Change", datasetKey);
    storeAllImageSizes(img, s -> cfg.datasetLogo(datasetKey, s));
  }

  @Override
  public void copyDatasetLogo(int datasetKey, int toDatasetKey) throws IOException {
    LOG.info("Copy logo for dataset {} to {}", datasetKey, toDatasetKey);
    for (ImgConfig.Scale scale : ImgConfig.Scale.values()) {
      Path src = cfg.datasetLogo(datasetKey, scale);
      if (Files.exists(src)) {
        Path target = cfg.datasetLogo(toDatasetKey, scale);
        Files.createDirectories(target.getParent());
        Files.copy(src, target);
      }
    }
  }

  @Override
  public void archiveDatasetLogo(int releaseKey, int datasetKey) throws IOException {
    Path src = cfg.datasetLogo(datasetKey, ImgConfig.Scale.ORIGINAL);
    if (Files.exists(src)) {
      LOG.info("Archive logo for dataset {} in release {} from {}", datasetKey, releaseKey, src);
      Path target = cfg.datasetLogoArchived(releaseKey, datasetKey);
      Files.createDirectories(target.getParent());
      Files.copy(src, target);
    } else {
      LOG.debug("No logo existing for dataset {} to archive in release {}", datasetKey, releaseKey);
    }
  }

  private void storeAllImageSizes(BufferedImage img, Function<ImgConfig.Scale, Path> locator) throws IOException {
    try {
      if (img == null) {
        LOG.debug("Delete all sizes for image {}", locator.apply(ImgConfig.Scale.ORIGINAL));
        for (ImgConfig.Scale scale : ImgConfig.Scale.values()) {
          Path p = locator.apply(scale);
          Files.delete(p);
        }
      } else {
        Path parent = locator.apply(ImgConfig.Scale.ORIGINAL).getParent();
        if (!Files.isDirectory(parent)) {
          Files.createDirectories(parent);
        }
        writeImage(locator.apply(ImgConfig.Scale.ORIGINAL), img);
        writeImage(locator.apply(ImgConfig.Scale.LARGE), scale(img, ImgConfig.Scale.LARGE));
        writeImage(locator.apply(ImgConfig.Scale.MEDIUM), scale(img, ImgConfig.Scale.MEDIUM));
        writeImage(locator.apply(ImgConfig.Scale.SMALL), scale(img, ImgConfig.Scale.SMALL));
      }
    } catch (IOException e) {
      LOG.error("Failed to update all sizes for image {} {}", locator.apply(ImgConfig.Scale.ORIGINAL), img, e);
    }
  }
  
  /**
   * Scales the image keeping proportions and restricting primarily by the images height.
   */
  private BufferedImage scale(BufferedImage raw, ImgConfig.Scale scale) {
    if (scale == ImgConfig.Scale.ORIGINAL) {
      return raw;
    }
    final Size size = cfg.size(scale);
    return Scalr.resize(raw, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_TO_HEIGHT, size.getWidth(), size.getHeight());
  }
  
  private void writeImage(Path p, BufferedImage img) throws IOException {
    LOG.debug("Writing new image {}", p);
    ImageIO.write(img, IMAGE_FORMAT, p.toFile());
  }
  
  
  @Override
  public BufferedImage datasetLogo(int datasetKey, ImgConfig.Scale scale) throws NotFoundException {
    Path p = cfg.datasetLogo(datasetKey, scale);
    return readImage(p, "Dataset " + datasetKey + " has no logo");
  }

  @Override
  public BufferedImage archiveDatasetLogo(int datasetKey, int releaseKey, ImgConfig.Scale scale) throws NotFoundException {
    Path p = cfg.datasetLogoArchived(releaseKey, datasetKey);
    BufferedImage img = readImage(p, "Dataset " + datasetKey + " has no logo in release " + releaseKey);
    // we only archive originals, we need to scale now on the fly
    return scale(img, scale);
  }

  private BufferedImage readImage(Path p, String notFoundMsg) throws NotFoundException {
    try {
      if (!Files.exists(p)) {
        throw new NotFoundException(notFoundMsg);
      }
      return ImageIO.read(p.toFile());
    } catch (IOException e) {
      throw new RuntimeException("Failed to read image " + p, e);
    }
  }
}
