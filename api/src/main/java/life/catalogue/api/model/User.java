package life.catalogue.api.model;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.security.auth.Subject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Country;
import life.catalogue.common.collection.CollectionUtils;
import org.checkerframework.checker.units.qual.K;

public class User implements Entity<Integer>, Principal {

  public enum Role {
    EDITOR,
    ADMIN
  }

  /**
   * Returns the user key or null if no user was given
   */
  public static Integer userkey(Optional<User> user){
    return user.map(User::getKey).orElse(null);
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
  private Map<String, String> settings = new HashMap<>();
  private IntSet datasets = new IntOpenHashSet();
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
    datasets = src.datasets;
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
  
  public boolean hasRole(String role, Integer datasetKey) {
    try {
      Role r = Role.valueOf(role.trim().toUpperCase());
      return hasRole(r, datasetKey);
      
    } catch (IllegalArgumentException e) {
      // swallow, we dont know this role so the user doesnt have it.
    }
    return false;
  }

  /**
   * Checks if a user has the given role and evaluates for the {@link Role#EDITOR} role also the given datasetKey
   */
  public boolean hasRole(Role role, Integer datasetKey) {
    // the editor role is scoped by datasetKey, see https://github.com/CatalogueOfLife/backend/issues/580
    return roles.contains(role) &&
      (role == Role.ADMIN || datasetKey == null || isEditor(datasetKey)
    );
  }

  /**
   * @return true if the user is an {@link Role#ADMIN} or {@link Role#EDITOR} with the given datasetKey listed in datasets
   */
  public boolean isAuthorized(Integer datasetKey) {
    if (datasetKey == null) return true;
    return roles.contains(Role.ADMIN) || isEditor(datasetKey);
  }

  @VisibleForTesting
  boolean isEditor(int datasetKey) {
    return roles.contains(Role.EDITOR) && datasets.contains(datasetKey);
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

  public IntSet getDatasets() {
    return datasets;
  }

  public void setDatasets(IntSet datasets) {
    this.datasets = datasets == null ? new IntOpenHashSet() : datasets;
    if (CollectionUtils.notEmpty(datasets)) {
      roles.add(Role.EDITOR);
    }
  }

  public void addDataset(int datasetKey) {
    datasets.add(datasetKey);
    roles.add(Role.EDITOR);
  }

  public void removeDataset(int datasetKey) {
    datasets.remove(datasetKey);
    if (datasets.isEmpty()) {
      roles.remove(Role.EDITOR);
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
        Objects.equals(datasets, user.datasets) &&
        Objects.equals(lastLogin, user.lastLogin) &&
        Objects.equals(created, user.created) &&
        Objects.equals(deleted, user.deleted);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, username, firstname, lastname, email, orcid, country, roles, settings, datasets, lastLogin, created, deleted);
  }
  
  @Override
  public String toString() {
    return username + " {" + key + "}";
  }
}
