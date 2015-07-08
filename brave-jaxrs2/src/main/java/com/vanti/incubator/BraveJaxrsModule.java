package com.vanti.incubator;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.jaxrs2.BraveClientRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveClientResponseFilter;
import com.github.kristofa.brave.jaxrs2.BraveContainerRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveContainerResponseFilter;

/**
 * @author Matt
 */
public class BraveJaxrsModule extends AbstractModule {
  @Override
  protected void configure() {
  }

  @Provides
  BraveContainerRequestFilter provideBraveContainerRequestFilter(ServerTracer serverTracer,
                                                                 EndPointSubmitter endPointSubmitter) {
    return new BraveContainerRequestFilter(serverTracer, endPointSubmitter);
  }

  @Provides
  BraveContainerResponseFilter provideBraveContainerResponseFilter(ServerTracer serverTracer) {
    return new BraveContainerResponseFilter(serverTracer);
  }

  @Provides
  BraveClientRequestFilter provideBraveClientRequestFilter(ClientTracer clientTracer) {
    return new BraveClientRequestFilter(clientTracer, Optional.<String>absent());
  }

  @Provides
  BraveClientResponseFilter provideBraveClientResponseFilter(ClientTracer clientTracer) {
    return new BraveClientResponseFilter(clientTracer, Optional.<String>absent());
  }
}
