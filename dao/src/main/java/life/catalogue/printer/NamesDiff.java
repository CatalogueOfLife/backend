package life.catalogue.printer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NamesDiff {
  private final Object key;
  private final int attempt1;
  private final int attempt2;
  private Set<String> deleted = new HashSet<>();
  private Set<String> inserted= new HashSet<>();
  
  public NamesDiff(Object key, int attempt1, int attempt2) {
    this.key = key;
    this.attempt1 = attempt1;
    this.attempt2 = attempt2;
  }
  
  public Object getKey() {
    return key;
  }
  
  public int getAttempt1() {
    return attempt1;
  }
  
  public int getAttempt2() {
    return attempt2;
  }
  
  public Set<String> getDeleted() {
    return deleted;
  }

  public void setDeleted(Set<String> deleted) {
    this.deleted = deleted == null ? Collections.EMPTY_SET : deleted;
  }

  public void setInserted(Set<String> inserted) {
    this.inserted = inserted == null ? Collections.EMPTY_SET : inserted;
  }

  public Set<String> getInserted() {
    return inserted;
  }

  public boolean isIdentical(){
    return deleted.isEmpty() && inserted.isEmpty();
  }

  @Override
  public String toString() {
    return "NamesDiff{" +
        "key=" + key +
        ", attempt1=" + attempt1 +
        ", attempt2=" + attempt2 +
        ", deleted=" + deleted.size() +
        ", inserted=" + inserted.size() +
        '}';
  }
}
