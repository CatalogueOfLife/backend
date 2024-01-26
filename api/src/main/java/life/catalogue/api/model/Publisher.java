package life.catalogue.api.model;

import javax.validation.constraints.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * GBIF UUID based publisher to be used in projects and releases as sector publishers.
 */
public class Publisher extends DatasetScopedEntity<UUID> {
  @NotNull
  private String alias;
  @NotNull
  private String title;
  private String description;

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Publisher publisher = (Publisher) o;
    return Objects.equals(alias, publisher.alias) && Objects.equals(title, publisher.title) && Objects.equals(description, publisher.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), alias, title, description);
  }
}
