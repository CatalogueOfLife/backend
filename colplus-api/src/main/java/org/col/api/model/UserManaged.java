package org.col.api.model;

import java.time.LocalDateTime;

/**
 * Entity that can be created and modified by a user.
 */
public interface UserManaged {
  
  LocalDateTime getCreated();
  
  void setCreated(LocalDateTime created);
  
  Integer getCreatedBy();
  
  void setCreatedBy(Integer createdBy);
  
  /**
   * The time the entity was last modified . Also set on creation!
   */
  LocalDateTime getModified();
  
  void setModified(LocalDateTime modified);
  
  /**
   * The user who has last modified the entity. Also set on creation!
   * @return ColUser key
   */
  Integer getModifiedBy();
  
  void setModifiedBy(Integer modifiedBy);
  
  default void applyUser(ColUser user) {
    applyUser(user.getKey());
  }
  
  default void applyUser(Integer userKey) {
    applyUser(userKey, false);
  }
  
  default void applyUser(Integer userKey, boolean updateCreator) {
    setModifiedBy(userKey);
    if (updateCreator || getCreatedBy() == null) {
      setCreatedBy(userKey);
    }
  }
}
