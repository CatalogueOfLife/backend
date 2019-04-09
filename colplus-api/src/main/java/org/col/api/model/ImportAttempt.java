package org.col.api.model;

import java.time.LocalDateTime;

public interface ImportAttempt {
  
  int getAttempt();
  
  void setAttempt(int attempt);
  
  LocalDateTime getStarted();
  
  void setStarted(LocalDateTime started);
  
  LocalDateTime getFinished();
  
  void setFinished(LocalDateTime finished);
  
  String getError();
  
  void setError(String error);
}
