package life.catalogue.api.txtree;

public class TreeLine {
  public final long line;
  public final int level;
  public final String content;

  public TreeLine(long line, int level, String content) {
    this.line = line;
    this.level = level;
    this.content = content;
  }
}
