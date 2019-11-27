package life.catalogue.release;

import com.google.common.base.Preconditions;
import org.slf4j.LoggerFactory;

public class Logger {
  private org.slf4j.Logger log;
  private final StringBuffer sb = new StringBuffer();
  
  public Logger() {
    this(LoggerFactory.getLogger(Logger.class));
  }

  public Logger(org.slf4j.Logger log) {
    this.log = log;
  }
  
  public void setLog(org.slf4j.Logger log) {
    this.log = Preconditions.checkNotNull(log);
  }
  
  public void log(String msg){
    if (msg != null) {
      log.info(msg);
      sb.append(msg);
      sb.append("\n");
    }
  }
  public String toString(){
    return sb.toString();
  }
}
