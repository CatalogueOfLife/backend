package life.catalogue.img;

import life.catalogue.api.exception.NotFoundException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

public interface ImageService {

  boolean delete(int datasetKey);

  boolean datasetLogoExists(int datasetKey);

  void putDatasetLogo(int datasetKey, BufferedImage img) throws IOException;

  void copyDatasetLogo(int datasetKey, int toDatasetKey) throws IOException;

  /**
   * Copies the original logo to a new file, using the image file format as given by the file suffix.
   * If no suffix is given PNG will be used
   */
  default void copyDatasetLogo(int datasetKey, File out) throws IOException {
    try {
      BufferedImage img = datasetLogo(datasetKey, ImgConfig.Scale.ORIGINAL);
      if (img != null) {
        String format = FilenameUtils.getExtension(out.getName());
        if (StringUtils.isEmpty(format)) format = "png";
        ImageIO.write(img, format, out);
      }
    } catch (NotFoundException e) {
      // nothing to do
    }
  }

  void archiveDatasetLogo(int releaseKey, int datasetKey) throws IOException;

  BufferedImage datasetLogo(int datasetKey, ImgConfig.Scale scale) throws NotFoundException;

  BufferedImage archiveDatasetLogo(int datasetKey, int releaseKey, ImgConfig.Scale scale) throws NotFoundException;

  
  static ImageService passThru() {
    return new ImageService() {
      @Override
      public boolean delete(int datasetKey) {
        return false;
      }

      @Override
      public boolean datasetLogoExists(int datasetKey) {
        return false;
      }

      @Override
      public void putDatasetLogo(int datasetKey, BufferedImage img) throws IOException {
      }

      @Override
      public void copyDatasetLogo(int datasetKey, int toDatasetKey) throws IOException {
      }

      @Override
      public void archiveDatasetLogo(int releaseKey, int datasetKey) throws IOException {
      }

      @Override
      public BufferedImage datasetLogo(int datasetKey, ImgConfig.Scale scale) {
        return null;
      }

      @Override
      public BufferedImage archiveDatasetLogo(int datasetKey, int releaseKey, ImgConfig.Scale scale) {
        return null;
      }
    };
  }
}
