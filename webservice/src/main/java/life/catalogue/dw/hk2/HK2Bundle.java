package life.catalogue.dw.hk2;

import java.util.Collection;
import java.util.stream.Stream;

import org.eclipse.jetty.util.component.LifeCycle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.servlet.ServletProperties;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.Lists;

import io.dropwizard.core.Application;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.cli.Command;
import io.dropwizard.core.setup.AdminEnvironment;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.servlets.tasks.Task;

import static org.glassfish.hk2.utilities.ServiceLocatorUtilities.bind;

public class HK2Bundle<T> implements ConfiguredBundle<T> {

  private final ServiceLocator serviceLocator;

  private Application application;

  public HK2Bundle() {
    this(Lists.newArrayList());
  }

  public HK2Bundle(Binder... binders) {
    this(Lists.newArrayList(binders));
  }

  private HK2Bundle(Collection<Binder> binders) {
    this.serviceLocator = ServiceLocatorUtilities.createAndPopulateServiceLocator();

    binders.forEach(binder -> ServiceLocatorUtilities.bind(serviceLocator, binder));

    listServices(Binder.class).forEach(binder -> bind(serviceLocator, binder));
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    this.application = bootstrap.getApplication();

    listServices(ConfiguredBundle.class).forEach(bootstrap::addBundle);
    listServices(Command.class).forEach(bootstrap::addCommand);
  }

  @Override
  public void run(T configuration, Environment environment) throws Exception {
    ServiceLocatorUtilities.bind(serviceLocator, new EnvBinder(application, environment));

    JerseyEnvironment jersey = environment.jersey();
    LifecycleEnvironment lifecycle = environment.lifecycle();
    AdminEnvironment admin = environment.admin();

    listServices(HealthCheck.class).forEach(healthCheck -> {
      String name = healthCheck.getClass().getSimpleName();
      environment.healthChecks().register(name, healthCheck);
    });

    listServices(Managed.class).forEach(lifecycle::manage);
    listServices(LifeCycle.class).forEach(lifecycle::manage);
    listServices(LifeCycle.Listener.class).forEach(lifecycle::addEventListener);
    listServices(ServerLifecycleListener.class).forEach(lifecycle::addServerLifecycleListener);
    listServices(Task.class).forEach(admin::addTask);

    //Set service locator as parent for Jersey's service locator
    environment.getApplicationContext().setAttribute(ServletProperties.SERVICE_LOCATOR, serviceLocator);
    environment.getAdminContext().setAttribute(ServletProperties.SERVICE_LOCATOR, serviceLocator);

    serviceLocator.inject(application);
    ServiceLocatorUtilities.addOneConstant(serviceLocator, configuration);
  }

  private <T> Stream<T> listServices(Class<T> type) {
    TypeFilter filter = new TypeFilter(type);
    return serviceLocator.getAllServices(filter).stream().map(type::cast);
  }

  private static class EnvBinder extends AbstractBinder {

    private final Application application;
    private final Environment environment;

    private EnvBinder(Application application, Environment environment) {
      this.application = application;
      this.environment = environment;
    }

    @Override
    protected void configure() {
      bind(application);
      bind(application).to(Application.class);
      bind(environment);
      bind(environment.getObjectMapper());
      bind(environment.metrics());
      bind(environment.getValidator());
    }
  }
}
