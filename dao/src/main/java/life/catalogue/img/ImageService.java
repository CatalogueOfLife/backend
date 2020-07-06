package life.catalogue.img;

import java.awt.image.BufferedImage;
import java.io.IOException;

public interface ImageService {
  
  void putDatasetLogo(int datasetKey, BufferedImage img) throws IOException;

  void archiveDatasetLogo(int releaseKey, int datasetKey) throws IOException;

  BufferedImage datasetLogo(int datasetKey, ImgConfig.Scale scale);

  BufferedImage archiveDatasetLogo(int datasetKey, int releaseKey, ImgConfig.Scale scale);

  
  static ImageService passThru() {
    return new ImageService() {
      @Override
      public void putDatasetLogo(int datasetKey, BufferedImage img) throws IOException {
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
