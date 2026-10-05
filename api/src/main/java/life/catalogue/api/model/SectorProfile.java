package life.catalogue.api.model;

import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Sync settings shared by every sector of a project its selector matches, so a setting for all Plazi sectors
 * lives in one place. Matching profiles cascade in ascending position, ties broken by id; the sector itself has
 * the last word. See docs/2026-10-05-sector-profiles.md.
 */
public class SectorProfile extends DatasetScopedEntity<Integer> {
  @NotBlank
  private String title;
  private String description;
  private int position;
  @NotNull
  @Valid
  private SectorSelector selector = new SectorSelector();
  @NotNull
  @Valid
  private SectorSettings settings = new SectorSettings();

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

  /**
   * @return the place in the cascade of matching profiles. A higher position overrides a lower one.
   */
  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public SectorSelector getSelector() {
    return selector;
  }

  public void setSelector(SectorSelector selector) {
    this.selector = selector == null ? new SectorSelector() : selector;
  }

  public SectorSettings getSettings() {
    return settings;
  }

  public void setSettings(SectorSettings settings) {
    this.settings = settings == null ? new SectorSettings() : settings;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorProfile)) return false;
    if (!super.equals(o)) return false;
    SectorProfile that = (SectorProfile) o;
    return position == that.position
           && Objects.equals(title, that.title)
           && Objects.equals(description, that.description)
           && Objects.equals(selector, that.selector)
           && Objects.equals(settings, that.settings);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), title, description, position, selector, settings);
  }

  @Override
  public String toString() {
    return "SectorProfile{" + getId() + ", datasetKey=" + getDatasetKey() + ", position=" + position + ", " + title + '}';
  }
}
