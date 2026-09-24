package life.catalogue.api.event;

public interface PersonListener extends Listener {

  void personsChanged(PersonsChanged event);

}
