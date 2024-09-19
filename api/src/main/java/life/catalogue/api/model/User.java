package life.catalogue.api.model;

import life.catalogue.api.vocab.Country;

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
  public static final int EDITOR_DEFAULT_DATASET = -1;

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
      if (u.isAdmin()) {
        return ADMIN_MAGIC_KEY;
      }
      return u.getKey();
    }
    return null;
  }

  private Integer key;
  @Nonnull
  private String username; // gbif managed
  private String firstname; // gbif managed
  private String lastname; // gbif managed
  private String email; // gbif managed
  private String orcid; // gbif managed
  private Country country; // gbif managed

  // global roles that apply to any dataset
  private final Set<Role> roles = EnumSet.noneOf(Role.class);

  // dataset specific editor role
  @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
  private final IntSet editor = new IntOpenHashSet();

  // dataset specific reviewer role
  @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
  private final IntSet reviewer = new IntOpenHashSet();

  // publisher specific editor role
  @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
  private final Set<UUID> publisher = new HashSet<>();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, String> settings = new HashMap<>();
  private LocalDateTime lastLogin;
  private LocalDateTime blocked;
  private LocalDateTime created;

  /**
   * Copies properties that are not managed in the GBIF registry to this instance.
   * Last login is also NOT copied!
   * @param src source user to copy from
   */
  public void copyNonGbifData(User src) {
    key = src.key;
    setRoles(src.roles);
    setEditor(src.editor);
    setReviewer(src.reviewer);
    setPublisher(src.publisher);
    settings = src.settings;
    blocked = src.blocked;
    created = src.created;
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

  @JsonIgnore
  public boolean isAdmin() {
    return roles.contains(Role.ADMIN);
  }

  public boolean isEditor() {
    return roles.contains(Role.EDITOR) || !editor.isEmpty();
  }
  public boolean isEditor(int datasetKey) {
    return roles.contains(Role.EDITOR) || editor.contains(datasetKey);
  }

  public boolean isReviewer(int datasetKey) {
    return roles.contains(Role.REVIEWER) || reviewer.contains(datasetKey);
  }

  /**
   * @return true if the user is managing datasets on behalf of a given gbif publisher organisation.
   */
  public boolean isPublisher(UUID publisherKey) {
    return publisherKey != null && publisher.contains(publisherKey);
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

  public void addRole(Role role) {
    if (role != null) {
      roles.add(role);
    }
  }

  public Set<Role> getRoles() {
    return roles;
  }

  public void setRoles(Set<Role> roles) {
    this.roles.clear();
    if (roles != null) {
      this.roles.addAll(roles);;
    }
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
    this.editor.clear();
    if (editor != null && !editor.isEmpty()) {
      this.editor.addAll(editor);;
    }
  }

  public IntSet getReviewer() {
    return reviewer;
  }

  public void setReviewer(IntSet reviewer) {
    this.reviewer.clear();
    if (reviewer != null && !reviewer.isEmpty()) {
      this.reviewer.addAll(reviewer);;
    }
  }

  public Set<UUID> getPublisher() {
    return publisher;
  }
  public void setPublisher(Set<UUID> publisher) {
    this.publisher.clear();
    if (publisher != null && !publisher.isEmpty()) {
      this.publisher.addAll(publisher);;
    }
  }

  public void addDatasetRole(User.Role role, int datasetKey) {
    switch (role) {
      case EDITOR:
        editor.add(datasetKey);
        break;
      case REVIEWER:
        reviewer.add(datasetKey);
        break;
      default:
        throw new IllegalArgumentException("Unsupported role " + role);
    }
  }

  public void removeDatasetRole(User.Role role, int datasetKey) {
    switch (role) {
      case EDITOR:
        editor.remove(datasetKey);
        break;
      case REVIEWER:
        reviewer.remove(datasetKey);
        break;
      default:
        throw new IllegalArgumentException("Unsupported role " + role);
    }
  }

  @JsonIgnore
  public boolean isBlockedUser() {
    return blocked != null;
  }

  public LocalDateTime getBlocked() {
    return blocked;
  }

  public void setBlocked(LocalDateTime blocked) {
    this.blocked = blocked;
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
           Objects.equals(editor, user.editor) &&
           Objects.equals(reviewer, user.reviewer) &&
           Objects.equals(settings, user.settings) &&
           Objects.equals(lastLogin, user.lastLogin) &&
           Objects.equals(created, user.created) &&
           Objects.equals(blocked, user.blocked);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, username, firstname, lastname, email, orcid, country, roles, editor, reviewer, settings, lastLogin, created, blocked);
  }

  @Override
  public String toString() {
    return username + " {" + key + "}";
  }
}
