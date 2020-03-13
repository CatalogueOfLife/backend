package life.catalogue.img;

import java.awt.image.BufferedImage;
import java.io.IOException;

public interface ImageService {
  
  void putDatasetLogo(int datasetKey, BufferedImage img) throws IOException;
  
  BufferedImage datasetLogo(int datasetKey, ImgConfig.Scale scale);
  
  
  
  static ImageService passThru() {
    return new ImageService() {
      @Override
      public void putDatasetLogo(int datasetKey, BufferedImage img) throws IOException {
      }
  
      @Override
      public BufferedImage datasetLogo(int datasetKey, ImgConfig.Scale scale) {
        return null;
      }
    };
  }
}
