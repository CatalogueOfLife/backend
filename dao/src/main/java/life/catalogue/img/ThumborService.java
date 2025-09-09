package life.catalogue.img;

import life.catalogue.api.model.Media;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.pollexor.Thumbor;


public class ThumborService {
  private static final Logger LOG = LoggerFactory.getLogger(ThumborService.class);
  private final ThumborConfig cfg;
  Thumbor thumbor;
  public ThumborService(ThumborConfig cfg) {
    this.cfg = cfg;
    if (!cfg.host.endsWith("/")) {
      cfg.host = cfg.host + "/";
    }
    thumbor = Thumbor.create(cfg.host, cfg.key);
  }

  public void addThumbnail(Media media) {
    if (media != null && media.getUrl() != null) {
      media.setThumbnail(thumbnail(media.getUrl()));
    }
  }

  public URI thumbnail(URI url) {
    return URI.create(
      thumbor.buildImage(url.toString())
        .resize(cfg.size*10, cfg.size)
        .fitIn()
        .toUrlSafe()
    );
  }

}
