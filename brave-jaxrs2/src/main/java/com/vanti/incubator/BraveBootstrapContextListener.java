package com.vanti.incubator;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import com.github.kristofa.brave.BraveExecutorService;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.jaxrs2.BraveClientRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveClientResponseFilter;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

/**
 * @author Matt
 */
public class BraveBootstrapContextListener extends GuiceResteasyBootstrapServletContextListener {

  @Nullable
  private ScheduledExecutorService scheduledExecutorService;
  @Nullable
  private Client client;

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
      ScheduledExecutorService provideScheduledExecutorService(ServerSpanThreadBinder serverSpanThreadBinder) {
        return new BraveScheduledExecutorService(Executors.newSingleThreadScheduledExecutor(), serverSpanThreadBinder);
      }

      @Provides
      @Singleton
      Client provideClient(BraveClientRequestFilter requestFilter, BraveClientResponseFilter responseFilter,
                           ServerSpanThreadBinder serverSpanThreadBinder) {
        return new ResteasyClientBuilder()
            // this configuration determines how many back-end requests we can perform in parallel
            .connectionPoolSize(20)
            .register(requestFilter)
            .register(responseFilter)
            .asyncExecutor(new BraveExecutorService(Executors.newFixedThreadPool(10), serverSpanThreadBinder))
            .build();
      }

      @Provides
      @Named("braveService")
      WebTarget provideFooServiceClient(Client client) {
        return client.target("http://{hostAndPort}/brave");
      }
    }, new BraveModule(), new BraveJaxrsModule());
  }

  @Inject
  void initClosableResources(ScheduledExecutorService scheduledExecutorService, Client client) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.client = client;
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {
    super.contextDestroyed(event);
    if (this.scheduledExecutorService != null) {
      this.scheduledExecutorService.shutdownNow();
      this.scheduledExecutorService = null;
    }
    if (this.client != null) {
      this.client.close();
      this.client = null;
    }
  }
}
