package life.catalogue.api.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * An event broadcast by the EventBroker to every app that shares the database, as a small JSON document.
 * Events carry keys and the few facts their listeners need, never whole entities, so the two app versions running
 * side by side during a deploy can still read each other's events. Unknown properties and types are ignored.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "t")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DatasetChanged.class, name = "dataset"),
  @JsonSubTypes.Type(value = DatasetDataChanged.class, name = "data"),
  @JsonSubTypes.Type(value = DatasetLogoChanged.class, name = "logo"),
  @JsonSubTypes.Type(value = UserChanged.class, name = "user"),
  @JsonSubTypes.Type(value = UserPermissionChanged.class, name = "perm"),
  @JsonSubTypes.Type(value = FlushCaches.class, name = "flush")
})
public interface Event {
}
