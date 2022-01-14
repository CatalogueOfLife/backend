package life.catalogue.api.model;

import life.catalogue.api.vocab.Country;
import life.catalogue.common.collection.CollectionUtils;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import javax.annotation.Nonnull;
import javax.security.auth.Subject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class User implements Entity<Integer>, Principal {

  public static final int ADMIN_MAGIC_KEY = -42;

  public enum Role {
    REVIEWER,
    EDITOR,
    ADMIN
  }

  /**
   * Returns the user key, -1 for admins or null if no user was given
   */
  public static Integer userkey(Optional<User> user){
    if (user.isPresent()) {
      User u = user.get();
      if (u.hasRole(Role.ADMIN)) {
        return ADMIN_MAGIC_KEY;
      }
      return u.getKey();
    }
    return null;
  }

  private Integer key;
  @Nonnull
  private String username;
  private String firstname;
  private String lastname;
  private String email;
  private String orcid;
  private Country country;
  private Set<Role> roles = EnumSet.noneOf(Role.class);
  @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
  private IntSet editor = new IntOpenHashSet();
  @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
  private IntSet reviewer = new IntOpenHashSet();
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, String> settings = new HashMap<>();
  private LocalDateTime lastLogin;
  private LocalDateTime created;
  private LocalDateTime deleted;

  public void addRole(Role role) {
    roles.add(role);
  }

  public void removeRole(Role role) {
    roles.remove(role);
  }

  /**
   * Copies properties that are not managed in the GBIF registry to this instance.
   * @param src source user to copy from
   */
  public void copyNonGbifData(User src) {
    key = src.key;
    roles = src.roles;
    editor = src.editor;
    settings = src.settings;
  }

  @Override
  @JsonIgnore
  public String getName() {
    return username;
  }
  
  @Override
  public boolean implies(Subject subject) {
    return false;
  }

  /**
   * Checks if a user has the given role. Note that the {@link Role#EDITOR} role is dataset specific.
   * Use hasRole(Role, Integer) to evaluate for a specific datasetKey.
   */
  public boolean hasRole(Role role) {
    return roles.contains(role);
  }

  public boolean isAdmin() {
    return hasRole(Role.ADMIN);
  }

  public boolean isEditor(int datasetKey) {
    return editor.contains(datasetKey);
  }

  public boolean isReviewer(int datasetKey) {
    return reviewer.contains(datasetKey);
  }

  public Integer getKey() {
    return key;
  }
  
  public void setKey(Integer key) {
    this.key = key;
  }
  
  public String getUsername() {
    return username;
  }
  
  public void setUsername(String username) {
    this.username = username;
  }
  
  public String getFirstname() {
    return firstname;
  }
  
  public void setFirstname(String firstname) {
    this.firstname = firstname;
  }
  
  public String getLastname() {
    return lastname;
  }
  
  public void setLastname(String lastname) {
    this.lastname = lastname;
  }
  
  public String getEmail() {
    return email;
  }
  
  public void setEmail(String email) {
    this.email = email;
  }
  
  public String getOrcid() {
    return orcid;
  }
  
  public void setOrcid(String orcid) {
    this.orcid = orcid;
  }
  
  public Country getCountry() {
    return country;
  }
  
  public void setCountry(Country country) {
    this.country = country;
  }
  
  public Set<Role> getRoles() {
    return roles;
  }
  
  public void setRoles(Set<Role> roles) {
    this.roles = roles;
  }
  
  public Map<String, String> getSettings() {
    return settings;
  }
  
  public void setSettings(Map<String, String> settings) {
    this.settings = settings;
  }

  public IntSet getEditor() {
    return editor;
  }

  public void setEditor(IntSet editor) {
    this.editor = editor == null ? new IntOpenHashSet() : editor;
    if (CollectionUtils.notEmpty(editor)) {
      roles.add(Role.EDITOR);
    }
  }

  public IntSet getReviewer() {
    return reviewer;
  }

  public void setReviewer(IntSet reviewer) {
    this.reviewer = reviewer == null ? new IntOpenHashSet() : reviewer;
    if (CollectionUtils.notEmpty(reviewer)) {
      roles.add(Role.REVIEWER);
    }
  }

  public void addDatasetRole(User.Role role, int datasetKey) {
    switch (role) {
      case EDITOR:
        editor.add(datasetKey);
        roles.add(role);
        break;
      case REVIEWER:
        reviewer.add(datasetKey);
        roles.add(role);
        break;
      default:
        throw new IllegalArgumentException("Unsupported role " + role);
    }
  }

  public void removeDatasetRole(User.Role role, int datasetKey) {
    switch (role) {
      case EDITOR:
        editor.remove(datasetKey);
        if (editor.isEmpty()) {
          roles.remove(role);
        }
        break;
      case REVIEWER:
        reviewer.remove(datasetKey);
        if (reviewer.isEmpty()) {
          roles.remove(role);
        }
        break;
      default:
        throw new IllegalArgumentException("Unsupported role " + role);
    }
  }

  public LocalDateTime getDeleted() {
    return deleted;
  }
  
  public void setDeleted(LocalDateTime deleted) {
    this.deleted = deleted;
  }
  
  public LocalDateTime getLastLogin() {
    return lastLogin;
  }
  
  public void setLastLogin(LocalDateTime lastLogin) {
    this.lastLogin = lastLogin;
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
  
  public void setCreated(LocalDateTime created) {
    this.created = created;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return Objects.equals(key, user.key) &&
           Objects.equals(username, user.username) &&
           Objects.equals(firstname, user.firstname) &&
           Objects.equals(lastname, user.lastname) &&
           Objects.equals(email, user.email) &&
           Objects.equals(orcid, user.orcid) &&
        country == user.country &&
           Objects.equals(roles, user.roles) &&
           Objects.equals(settings, user.settings) &&
           Objects.equals(editor, user.editor) &&
           Objects.equals(lastLogin, user.lastLogin) &&
           Objects.equals(created, user.created) &&
           Objects.equals(deleted, user.deleted);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, username, firstname, lastname, email, orcid, country, roles, settings, editor, lastLogin, created, deleted);
  }
  
  @Override
  public String toString() {
    return username + " {" + key + "}";
  }
}
