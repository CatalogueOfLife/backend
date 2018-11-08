package org.col.img;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import javax.imageio.ImageIO;

import org.col.api.exception.NotFoundException;
import org.col.api.model.Dataset;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageService {
  public static final String IMAGE_FORMAT = "png";
  private static final Logger LOG = LoggerFactory.getLogger(ImageService.class);
  
  private final ImgConfig cfg;
  
  public ImageService(ImgConfig cfg) {
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
  
  public void putDatasetLogo(Dataset dataset, BufferedImage img) throws IOException {
    // is it allowed?
    if (dataset.getLogo() != null) {
      throw new IllegalArgumentException("Dataset is already configured with an external logo URL " + dataset.getLogo());
    }
    storeAllImageSizes(img, s -> cfg.datasetLogo(dataset.getKey(), s));
  }
  
  public void putColSourceLogo(int colSourceKey, BufferedImage img) throws IOException {
    storeAllImageSizes(img, s -> cfg.sourceLogo(colSourceKey, s));
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
        writeImage(locator.apply(ImgConfig.Scale.SMALL), scale(img, ImgConfig.Scale.SMALL));
      }
    } catch (IOException e) {
      LOG.error("Failed to update all sizes for image {} {}", locator.apply(ImgConfig.Scale.ORIGINAL), img, e);
    }
  }
  
  private BufferedImage scale(BufferedImage raw, ImgConfig.Scale scale) {
    final Size size = cfg.size(scale);
    return Scalr.resize(raw, Scalr.Method.ULTRA_QUALITY, size.getWidth(), size.getHeight());
  }
  
  private void writeImage(Path p, BufferedImage img) throws IOException {
    LOG.debug("Writing new image {}", p);
    ImageIO.write(img, IMAGE_FORMAT, p.toFile());
  }
  
  
  public BufferedImage datasetLogo(int datasetKey, ImgConfig.Scale scale) {
    Path p = cfg.datasetLogo(datasetKey, scale);
    return readImage(p, "Dataset " + datasetKey + " has no logo");
  }
  
  public BufferedImage colSourceLogo(int colSourceKey, ImgConfig.Scale scale) {
    Path p = cfg.sourceLogo(colSourceKey, scale);
    return readImage(p, "Source " + colSourceKey + " has no logo");
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
