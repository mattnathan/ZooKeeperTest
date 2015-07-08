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
  private final WebTarget fooService;

  @Inject
  public BraveResource(ScheduledExecutorService delayService, @Named("fooService") WebTarget fooService) {
    this.delayService = checkNotNull(delayService);
    this.fooService = checkNotNull(fooService);
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
   * @param howMany How many echo requests should we send
   * @param asyncResponse Will be resumed when a result is available.
   */
  @GET
  @Path("/call/{howMany}")
  public void callOtherServices(@PathParam("howMany") @DefaultValue("3") int howMany,
                                @Suspended AsyncResponse asyncResponse) {
    configureAsyncResponseTimeout(asyncResponse);

    // this task takes a while to complete, kick it off first so it has just that little bit longer to do its job
    CompletableFuture<String> longTask = getAsync(fooService.path("/brave/delay/3"));

    // this is the set of all values from 0 -> howMany (exclusive)
    ContiguousSet<Integer> values = ContiguousSet.create(Range.closedOpen(0, howMany), DiscreteDomain.integers());
    List<CompletableFuture<String>> backendResponses =
        values.stream()
            // compute the requests we are about to make
            .map((val) -> fooService.path("/brave/echo/Value-" + val))
                // kick off all the backend requests
            .map(this::getAsync)
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

  private void configureAsyncResponseTimeout(@Suspended AsyncResponse asyncResponse) {
    if (!asyncResponse.setTimeout(20, SECONDS)) {
      System.out.println("Couldn't change the timeout of the asyncRequest");
    }
  }

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

  public CompletableFuture<String> getAsync(WebTarget target) {
    return getAsync(target.request());
  }

  public CompletableFuture<String> getAsync(Invocation.Builder target) {
    CompletableFuture<String> result = new CompletableFuture<>();
    target.async().get(new InvocationCallback<String>() {
      @Override
      public void completed(String s) {
        result.complete(s);
      }

      @Override
      public void failed(Throwable throwable) {
        result.completeExceptionally(throwable);
      }
    });
    return result;
  }

  public <T> CompletionStage<List<? extends T>> allOf(Collection<CompletableFuture<T>> items) {
    return CompletableFuture.allOf(items.toArray(new CompletableFuture[items.size()]))
                            .thenApply((o) -> items.stream().map(CompletableFuture::join).collect(toList()));
  }
}
