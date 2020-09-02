package life.catalogue.img;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertNotNull;

public class ImageServiceFSTest {

  ImageServiceFS srv;
  private ImgConfig cfg;

  @Before
  public void init() {
    cfg = new ImgConfig();
    cfg.repo = new File("target/imgtest/repo").toPath();
    cfg.archive = new File("target/imgtest/archive").toPath();
    srv = new ImageServiceFS(cfg);
  }

  @After
  public void clear() throws IOException {
    FileUtils.deleteDirectory(new File("target/imgtest"));
  }

  BufferedImage logo(String filename) throws IOException {
    InputStream res = Resources.stream("logos/"+filename);
    return ImageIO.read(res);
  }

  @Test
  public void roundtrip() throws IOException {
    final int datasetKey = 3;
    final int sourceDatasetKey = 1010;

    srv.putDatasetLogo(sourceDatasetKey, logo("fishbase.png"));
    assertNotNull(srv.datasetLogo(sourceDatasetKey, ImgConfig.Scale.ORIGINAL));
    assertNotNull(srv.datasetLogo(sourceDatasetKey, ImgConfig.Scale.SMALL));
    assertNotNull(srv.datasetLogo(sourceDatasetKey, ImgConfig.Scale.MEDIUM));
    assertNotNull(srv.datasetLogo(sourceDatasetKey, ImgConfig.Scale.LARGE));

    srv.archiveDatasetLogo(datasetKey, sourceDatasetKey);
    assertNotNull(srv.archiveDatasetLogo(sourceDatasetKey, datasetKey, ImgConfig.Scale.ORIGINAL));
  }

  @Test(expected = NotFoundException.class)
  public void notFound() throws IOException {
    final int datasetKey = 3;
    final int sourceDatasetKey = 1010;
    srv.archiveDatasetLogo(sourceDatasetKey, datasetKey, ImgConfig.Scale.ORIGINAL);
  }
}