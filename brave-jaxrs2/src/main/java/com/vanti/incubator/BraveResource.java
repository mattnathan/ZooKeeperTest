package com.vanti.incubator;

import com.google.common.base.Joiner;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.AsyncInvoker;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.nCopies;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

/**
 * @author Matt
 */
@Path("/brave")
public class BraveResource {

  private static final Joiner NEW_LINES = Joiner.on('\n');

  private final ScheduledExecutorService delayService;
  private final WebTarget braveService;
  private final BraveService service;

  @Inject
  public BraveResource(ScheduledExecutorService delayService, @Named("braveService") WebTarget braveService,
                       BraveService service) {
    this.delayService = checkNotNull(delayService);
    this.braveService = checkNotNull(braveService);
    this.service = checkNotNull(service);
  }

  /**
   * Simple echo function that prints out three of whatever you give in the path.
   *
   * @param content The content to print out
   * @return "{content}, {content}, {content}"
   */
  @GET
  @Path("/echo/{text}")
  public String echo(@PathParam("text") String content) {
    return Joiner.on(", ").join(nCopies(3, content));
  }

  /**
   * Simple endpoint function that asynchronously waits the given number of seconds before returning.
   *
   * @param age           The delay in seconds
   * @param asyncResponse Will be resumed when a result is available.
   */
  @GET
  @Path("/delay/{age}")
  public void delay(@PathParam("age") int age, @Suspended AsyncResponse asyncResponse) {
    configureAsyncResponseTimeout(asyncResponse);
    schedule(() -> "Your request was delayed by " + age + " seconds", age, SECONDS)
        .thenAccept(asyncResponse::resume)
        .exceptionally((e) -> {
          asyncResponse.resume(e);
          return null;
        });
  }

  /**
   * Complex endpoint function that calls {@code howMany} backend echo requests plus a single delay request. All this
   * happens in parallel and the function completes asynchronously.
   *
   * @param howMany       How many echo requests should we send
   * @param asyncResponse Will be resumed when a result is available.
   */
  @GET
  @Path("/call/{howMany}")
  public void callOtherServices(@PathParam("howMany") @DefaultValue("3") int howMany,
                                @Suspended AsyncResponse asyncResponse) {
    configureAsyncResponseTimeout(asyncResponse);

    // this task takes a while to complete, kick it off first so it has just that little bit longer to do its job
    CompletableFuture<String> longTask = service.asyncGet(braveService.path("/delay/3"));

    // this is the set of all values from 0 -> howMany (exclusive)
    ContiguousSet<Integer> values = ContiguousSet.create(Range.closedOpen(0, howMany), DiscreteDomain.integers());
    List<CompletableFuture<String>> backendResponses =
        values.stream()
            // compute the requests we are about to make
            .map((val) -> braveService.path("/echo/Value-" + val))
                // kick off all the backend requests
            .map(service::asyncGet)
                // collect the promises of responses for those requests in a collection
            .collect(toList());

    // add the long running task to the end of the triggered backend calls
    backendResponses.add(longTask);

    // once all the requests have completed
    allOf(backendResponses)
        // place one response per new line
        .thenApply(NEW_LINES::join)
            // respond to the client
        .thenAccept(asyncResponse::resume)
        .exceptionally((t) -> {
          asyncResponse.resume(t);
          return null;
        });
  }

  /**
   * Wrapper around {@link ScheduledExecutorService#schedule(Callable, long, TimeUnit)} that returns a
   * {@link CompletableFuture} instead.
   *
   * @param callable the task to execute
   * @param delay    the time from now to delay execution
   * @param timeUnit the time unit of the delay parameter
   * @param <T>      the type of the callable's result
   * @return The future response from the callable.
   */
  private <T> CompletableFuture<T> schedule(Callable<T> callable, long delay, TimeUnit timeUnit) {
    CompletableFuture<T> future = new CompletableFuture<>();
    delayService.schedule(() -> {
      try {
        future.complete(callable.call());
      } catch (Throwable e) {
        future.completeExceptionally(e);
      }
    }, delay, timeUnit);
    return future;
  }

  /**
   * Implementation of the {@link CompletableFuture#allOf(CompletableFuture[])} that returns all the resolved items
   * instead of {@link Void}.
   *
   * @param items All the futures that we should wait for.
   * @param <T>   The type of response that all the futures resolve to.
   * @return A new {@link CompletableFuture} that will resolve when all the given items have resolved.
   * @see CompletableFuture#allOf(CompletableFuture[])
   */
  public <T> CompletableFuture<List<? extends T>> allOf(Collection<CompletableFuture<T>> items) {
    return CompletableFuture.allOf(items.toArray(new CompletableFuture[items.size()]))
                            .thenApply((o) -> items.stream().map(CompletableFuture::join).collect(toList()));

  }

  /**
   * Helper function that configures the {@link AsyncResponse} so that the timeout is greater.
   *
   * @param asyncResponse The response to configure.
   */
  private void configureAsyncResponseTimeout(@Suspended AsyncResponse asyncResponse) {
    // see https://issues.jboss.org/browse/RESTEASY-1194 for why we need to do this
    if (!asyncResponse.setTimeout(20, SECONDS)) {
      System.out.println("Couldn't change the timeout of the asyncRequest");
    }
  }
}
