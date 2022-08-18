package life.catalogue.img;

import life.catalogue.db.PgDbConfig;

import java.nio.file.Path;

import javax.validation.constraints.NotNull;

/**
 * A configuration for the postgres database connection pool as used by the mybatis layer.
 */
@SuppressWarnings("PublicField")
public class ImgConfig extends PgDbConfig {
  
  public enum Scale {
    ORIGINAL,
    LARGE,
    MEDIUM,
    SMALL
  }
  
  @NotNull
  public Path repo;

  @NotNull
  public Path archive;

  @NotNull
  public Size small = new Size(30, 90);
  
  @NotNull
  public Size medium = new Size(100, 300);

  @NotNull
  public Size large = new Size(200, 600);
  
  public Size size(Scale scale) {
    switch (scale) {
      case LARGE:
        return large;
      case MEDIUM:
        return medium;
      case SMALL:
        return small;
    }
    throw new IllegalArgumentException("No raw size supported");
  }

  /**
   * @return path to the original source dataset logo at the time the given release was created.
   */
  public Path datasetLogoArchived(int releaseKey, int datasetKey) {
    return archive == null ? null : archive.resolve("release/"+releaseKey+"/dataset").resolve(filename(datasetKey, Scale.ORIGINAL));
  }

  public Path datasetLogo(int datasetKey, Scale scale) {
    return repo.resolve("dataset").resolve(filename(datasetKey, scale));
  }
  
  private String filename(int datasetKey, Scale scale) {
    return String.format("%s-logo-%s.%s", datasetKey, scale.name().toLowerCase(), ImageServiceFS.IMAGE_FORMAT);
  }
  
}
