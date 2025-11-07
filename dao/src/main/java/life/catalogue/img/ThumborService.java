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
    if (cfg.key != null) {
      thumbor = Thumbor.create(cfg.host, cfg.key);
    } else {
      thumbor = Thumbor.create(cfg.host);
    }
  }

  public void addThumbnail(Media media) {
    if (media != null && media.getUrl() != null) {
      media.setThumbnail(thumbnail(media.getUrl()));
    }
  }

  public URI thumbnail(URI url) {
    var builder = thumbor.buildImage(urlEscape(url))
      .resize(cfg.size*10, cfg.size)
      .fitIn();
    return URI.create(builder.toUrl());
  }

  static String urlEscape(URI url) {
    return url.toString().replaceFirst("\\?", "%3F");
  }

}
