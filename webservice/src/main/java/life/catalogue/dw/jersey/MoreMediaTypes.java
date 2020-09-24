package life.catalogue.dw.jersey;

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

  public final static String APP_YAML = "application/x-yaml";

  public final static String APP_JSON_COLDP = "application/vnd.coldp+json";
  public final static String APP_JSON_CSL   = "application/vnd.citationstyles.csl+json";

  public final static String APP_XLS   = "application/vnd.ms-excel";
  public final static String APP_XLSX  = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  public final static String TEXT_YAML   = "text/yaml";
  public final static String TEXT_CSV   = "text/csv";
  public final static String TEXT_TSV   = "text/tsv";
  public final static String TEXT_COMMA_SEP   = "text/comma-separated-values";
  public final static String TEXT_TAB_SEP   = "text/tab-separated-values";
  public final static String TEXT_WILDCARD   = "text/*";
  
  private MoreMediaTypes() {
  }
  
}
