package life.catalogue.api.model;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import javax.annotation.Nonnull;
import javax.security.auth.Subject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import life.catalogue.api.vocab.Country;

public class ColUser implements Principal {
  public enum Role {
    USER,
    EDITOR,
    ADMIN
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
  private LocalDateTime lastLogin;
  private LocalDateTime created;
  private LocalDateTime deleted;
  
  public void addRole(Role role) {
    roles.add(role);
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
  
  public boolean hasRole(String role) {
    try {
      Role r = Role.valueOf(role.trim().toUpperCase());
      return hasRole(r);
      
    } catch (IllegalArgumentException e) {
      // swallow, we dont know this role so the user doesnt have it.
    }
    return false;
  }
  
  public boolean hasRole(Role role) {
    return roles.contains(role);
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
    ColUser user = (ColUser) o;
    return Objects.equals(key, user.key) &&
        Objects.equals(username, user.username) &&
        Objects.equals(firstname, user.firstname) &&
        Objects.equals(lastname, user.lastname) &&
        Objects.equals(email, user.email) &&
        Objects.equals(orcid, user.orcid) &&
        country == user.country &&
        Objects.equals(roles, user.roles) &&
        Objects.equals(settings, user.settings) &&
        Objects.equals(lastLogin, user.lastLogin) &&
        Objects.equals(created, user.created) &&
        Objects.equals(deleted, user.deleted);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, username, firstname, lastname, email, orcid, country, roles, settings, lastLogin, created, deleted);
  }
  
  @Override
  public String toString() {
    return username + " {" + key + "}";
  }
}
