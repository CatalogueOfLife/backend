package life.catalogue.api.event;

public interface DoiListener extends Listener {

  void doiChanged(DoiChange event);

}
