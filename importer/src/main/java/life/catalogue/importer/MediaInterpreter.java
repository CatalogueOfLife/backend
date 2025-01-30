package life.catalogue.importer;

import life.catalogue.api.model.Media;
import life.catalogue.api.vocab.MediaType;

import java.net.URI;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.tika.Tika;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

public class MediaInterpreter {
  private static final Logger LOG = LoggerFactory.getLogger(MediaInterpreter.class);
  private static final Tika TIKA = new Tika();
  private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();
  private static final String HTML_TYPE = "text/html";
  // mime types which we consider as html links instead of real media file uris
  private static final Set<String> HTML_MIME_TYPES = ImmutableSet
    .of("text/x-coldfusion", "text/x-php", "text/asp", "text/aspdotnet", "text/x-cgi", "text/x-jsp", "text/x-perl",
      HTML_TYPE, MIME_TYPES.OCTET_STREAM);
  
  /**
   * Tries to derive a media type (image/video/audio) from the format, url or explicitly given type.
   */
  public static Media detectType(Media m) {
    if (Strings.isNullOrEmpty(m.getFormat())) {
      // derive from URI
      m.setFormat(parseMimeType(m.getUrl()));
    }

    // if MIME type is text/html make it a references link instead
    if (HTML_TYPE.equalsIgnoreCase(m.getFormat()) && m.getUrl() != null) {
      // make file URI the references link URL instead
      m.setLink(m.getUrl());
      m.setUrl(null);
      m.setFormat(null);
    }

    if (!Strings.isNullOrEmpty(m.getFormat())) {
      if (m.getFormat().startsWith("image")) {
        m.setType(MediaType.IMAGE);
      } else if (m.getFormat().startsWith("audio")) {
        m.setType(MediaType.AUDIO);
      } else if (m.getFormat().startsWith("video")) {
        m.setType(MediaType.VIDEO);
      } else {
        LOG.debug("Unsupported media format {}", m.getFormat());
      }
    }
    return m;
  }

  /**
   * Parses a mime type using apache tika which can handle the following:
   * http://svn.apache.org/repos/asf/tika/trunk/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml
   */
  public static String parseMimeType(@Nullable String format) {
    if (format != null) {
      format = Strings.emptyToNull(format.trim().toLowerCase());
    }

    try {
      MimeType mime = MIME_TYPES.getRegisteredMimeType(format);
      if (mime != null) {
        return mime.getName();
      }

    } catch (MimeTypeException e) {
    }

    // verify this is a reasonable mime type
    return format == null || MimeType.isValid(format) ? format : null;
  }

  /**
   * Parses a mime type using apache tika which can handle the following:
   * http://svn.apache.org/repos/asf/tika/trunk/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml
   */
  private static String parseMimeType(@Nullable URI uri) {
    if (uri != null) {
      String mime = TIKA.detect(uri.toString());
      if (mime != null && HTML_MIME_TYPES.contains(mime.toLowerCase())) {
        // links without any suffix default to OCTET STREAM, see:
        // http://dev.gbif.org/issues/browse/POR-2066
        return HTML_TYPE;
      }
      return mime;
    }
    return null;
  }
}
