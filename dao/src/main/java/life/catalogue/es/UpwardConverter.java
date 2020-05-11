package life.catalogue.es;

/**
 * A tag interface to distinguish and make a mental trail through all the converters in the es package. Note that some low-level objects are
 * not converted directly into API objects but instead into an intermediary object, which is why we don't use a more specific name for this
 * interface.
 *
 * @param <LOWER> The lower-level object that is going to be converted
 * @param <HIGHER> The higher-level object that is going to be produced.
 */
public interface UpwardConverter<LOWER, HIGHER> {

}
