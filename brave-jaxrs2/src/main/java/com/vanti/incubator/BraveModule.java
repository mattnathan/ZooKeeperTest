package com.vanti.incubator;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

import com.github.kristofa.brave.*;

import java.util.List;
import java.util.Set;

import javax.inject.Singleton;

/**
 * @author Matt
 */
public class BraveModule extends AbstractModule {
  @Override
  protected void configure() {
    Multibinder.newSetBinder(binder(), TraceFilter.class);
  }

  @Provides
  SpanCollector provideSpanCollector() {
    return new LoggingSpanCollectorImpl();
  }

  @Provides
  @Singleton
  List<TraceFilter> provideTraceFilters(Set<TraceFilter> filters) {
    return ImmutableList.copyOf(filters);
  }

  @Provides
  @Singleton
  ClientTracer provideClientTracer(SpanCollector spanCollector, List<TraceFilter> filters) {
    return Brave.getClientTracer(spanCollector, filters);
  }

  @Provides
  @Singleton
  ServerTracer provideServerTracer(SpanCollector spanCollector, List<TraceFilter> filters) {
    return Brave.getServerTracer(spanCollector, filters);
  }

  @Provides
  @Singleton
  EndPointSubmitter provideEndPointSubmitter() {
    return Brave.getEndPointSubmitter();
  }

  @Provides
  @Singleton
  ServerSpanThreadBinder provideServerSpanThreadBinder() {
    return Brave.getServerSpanThreadBinder();
  }

  @Provides
  @Singleton
  ClientSpanThreadBinder provideClientSpanThreadBinder() {
    return Brave.getClientSpanThreadBinder();
  }

  @Provides
  @Singleton
  AnnotationSubmitter provideAnnotationSubmitter() {
    return Brave.getServerSpanAnnotationSubmitter();
  }
}
