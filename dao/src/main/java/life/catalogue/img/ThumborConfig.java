package life.catalogue.img;

import java.net.URI;

import com.squareup.pollexor.ThumborUrlBuilder;

import jakarta.validation.constraints.NotNull;

/**
 * A configuration for a thumbor image cache
 */
@SuppressWarnings("PublicField")
public class ThumborConfig {
  
  @NotNull
  public String host = "https://api.gbif.org/v1/image";

  @NotNull
  public String key;

  @NotNull
  public int size = 260;

  @NotNull
  public ThumborUrlBuilder.ImageFormat format = ThumborUrlBuilder.ImageFormat.WEBP;
}
