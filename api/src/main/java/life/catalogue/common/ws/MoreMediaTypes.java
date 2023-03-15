package life.catalogue.common.ws;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

public class MoreMediaTypes {
  public final static String IMG_BMP = "image/bmp";
  public final static String IMG_PNG = "image/png";
  public final static String IMG_JPG = "image/jpeg";
  public final static String IMG_GIF = "image/gif";
  public final static String IMG_TIFF = "image/tiff";
  public final static String IMG_PSD = "image/vnd.adobe.photoshop";
  
  public final static String APP_GZIP = "application/gzip";
  public final static String APP_GZIP_ALT1 = "application/x-gzip";
  public final static String APP_GZIP_ALT2 = "application/x-gtar";
  public final static String APP_GZIP_ALT3 = "application/x-tgz";
  public final static String APP_ZIP = "application/zip";
  public final static String APP_ZIP_ALT1 = "application/zip-compressed";
  public final static String APP_ZIP_ALT2 = "application/x-zip-compressed";
  public final static String APP_ZIP_ALT3 = "multipart/x-zip";

  public final static String APP_YAML = "application/yaml";
  public final static MediaType APP_YAML_TYPE = new MediaType("application", "yaml");

  public final static String APP_JSON_COLDP = "application/vnd.coldp+json";
  public final static MediaType APP_JSON_COLDP_TYPE = new MediaType("application", "vnd.coldp+json");
  public final static String APP_JSON_CSL   = "application/vnd.citationstyles.csl+json";
  public final static MediaType APP_JSON_CSL_TYPE = new MediaType("application", "vnd.citationstyles.csl+json");
  public final static String APP_BIBTEX = "application/x-bibtex";
  public final static MediaType APP_BIBTEX_TYPE = new MediaType("application", "x-bibtex");

  public final static String APP_XLS   = "application/vnd.ms-excel";
  public final static String APP_XLSX  = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";


  public final static String TEXT_YAML  = "text/yaml";
  public final static MediaType TEXT_YAML_TYPE = new MediaType("text", "yaml");
  public final static String TEXT_CSV   = "text/csv";
  public final static MediaType TEXT_CSV_TYPE = new MediaType("text", "csv");
  public final static String TEXT_TSV   = "text/tsv";
  public final static MediaType TEXT_TSV_TYPE = new MediaType("text", "tsv");
  public final static String TEXT_MARKDOWN   = "text/markdown";
  public final static MediaType TEXT_MARKDOWN_TYPE = new MediaType("text", "markdown");
  public final static String TEXT_COMMA_SEP = "text/comma-separated-values";
  public final static String TEXT_TAB_SEP   = "text/tab-separated-values";
  public final static String TEXT_CSS      = "text/css";
  public final static String TEXT_WILDCARD = "text/*";
  
  private MoreMediaTypes() {
  }

  public static void setUTF8ContentType(MediaType mt, MultivaluedMap<String, Object> headers) {
    headers.putSingle(HttpHeaders.CONTENT_TYPE, mt.toString() + ";charset=UTF-8");
  }


}
