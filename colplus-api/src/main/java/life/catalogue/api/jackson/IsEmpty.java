package life.catalogue.api.jackson;

/**
 * Allows any class to indicate it has no content.
 * Used by a custom jackson filter to exclude such instances from being rendered.
 */
public interface IsEmpty {

  boolean isEmpty();
}
