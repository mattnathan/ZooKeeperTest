package com.vanti.incubator;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.security.auth.login.Configuration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

/**
 * @author Matt
 */
public class BraveBootstrapContextListener extends GuiceResteasyBootstrapServletContextListener {


  private ScheduledExecutorService scheduledExecutorService;

  @Override
  protected List<? extends Module> getModules(ServletContext context) {
    return ImmutableList.of(new AbstractModule() {
      @Override
      protected void configure() {
        bind(BraveResource.class);
        requestInjection(BraveBootstrapContextListener.this);
      }

      @Provides
      @Singleton
      ScheduledExecutorService provideScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
      }

      @Provides
      @Singleton
      Client provideClient() {
        return ClientBuilder.newClient();
      }

      @Provides
      @Named("fooService")
      WebTarget provideFooServiceClient(Client client) {
        return client.target("http://localhost:8080");
      }
    });
  }

  @Inject
  void initScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
    this.scheduledExecutorService = scheduledExecutorService;
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {
    super.contextDestroyed(event);
    if (this.scheduledExecutorService != null) {
      this.scheduledExecutorService.shutdownNow();
      this.scheduledExecutorService = null;
    }
  }
}
