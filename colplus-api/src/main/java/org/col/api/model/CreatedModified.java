package org.col.api.model;

import java.time.LocalDateTime;

/**
 * Entity that can be created and modified by a user.
 */
public interface CreatedModified {
  
  LocalDateTime getCreated();
  
  void setCreated(LocalDateTime created);
  
  int getCreatedBy();
  
  void setCreatedBy(int createdBy);
  
  /**
   * The time the entity was last modified . Also set on creation!
   */
  LocalDateTime getModified();
  
  void setModified(LocalDateTime modified);
  
  /**
   * The user who has last modified the entity. Also set on creation!
   * @return ColUser key
   */
  int getModifiedBy();
  
  void setModifiedBy(int modifiedBy);
}
