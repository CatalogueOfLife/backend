package org.col.img;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.col.api.model.Dataset;

public interface ImageService {
  
  default void putDatasetLogo(Dataset dataset, BufferedImage img) throws IOException {
    // is it allowed?
    if (dataset.getLogo() != null) {
      throw new IllegalArgumentException("Dataset is already configured with an external logo URL " + dataset.getLogo());
    }
    putDatasetLogo(dataset.getKey(), img);
  }

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
