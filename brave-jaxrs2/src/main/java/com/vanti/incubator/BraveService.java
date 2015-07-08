package com.vanti.incubator;

import com.google.common.net.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * @author Matt
 */
public class BraveService {
  private static final Logger LOGGER = LoggerFactory.getLogger(BraveService.class);
  private final BraveServiceLocation location;

  @Inject
  public BraveService(BraveServiceLocation location) {this.location = checkNotNull(location);}

  public CompletableFuture<String> asyncGet(WebTarget target) {
    CompletableFuture<String> result = null;
    // for each of the locations that are available we chain futures so that if one fails then the next location
    // is tried, all the way until we don't have any more locations in which case the final error is reported.
    // i.e. it will look a little like this:
    // do request a
    //  - if a failed try request b
    //  - if b failed try request c...
    // return last success or last failure response

    // todo: we should really record our failures in the service location system so that we can blacklist those
    // locations that fail and not try them again.

    // keep track of the last round of requests for error logging
    WebTarget lastResolvedTarget = null;

    for (HostAndPort hostAndPort : location.getLocations()) {
      // replace any placeholders in the uri with the given values.
      WebTarget resolvedTarget = target.resolveTemplate("hostAndPort", hostAndPort);

      if (result == null) {
        // first address always gets invoked
        result = directAsyncGet(resolvedTarget);
      } else {
        // all other addresses get linked with the failure path of the previous request attempt
        result = tryAgain(result, lastResolvedTarget, resolvedTarget);
      }
      lastResolvedTarget = resolvedTarget;
    }
    return result;
  }

  /**
   * Attach a new task to the failure branch of the given future that will attempt to use the given resolvedTarget to
   * try the request again.
   *
   * @param result             The existing future tree
   * @param lastResolvedTarget The last request target that was attempted - for logging.
   * @param resolvedTarget     The request target we should try if the last one fails
   * @return A new future that represents the new tree.
   */
  private CompletableFuture<String> tryAgain(CompletableFuture<String> result, WebTarget lastResolvedTarget,
                                             WebTarget resolvedTarget) {
    // this way is a little hacky, what we really want is thenComposeExceptionally(Function(Throwable):T)
    // but that doesn't exist, CompletableFuture is missing so many APIs :(
    BiFunction<String, Throwable, String> handlePossibleError = (r, e) -> {
      if (e != null) { // error case
        LOGGER.info("Endpoint resolution failed for " + lastResolvedTarget.getUri() +
                    " - trying " + resolvedTarget.getUri());
      }
      return r;
    };
    result = result.handle(handlePossibleError)
                   .thenCompose((r) -> r == null ? directAsyncGet(resolvedTarget) : completedFuture(r));
    return result;
  }

  private CompletableFuture<String> directAsyncGet(WebTarget resolvedTarget) {
    return asyncGet(resolvedTarget.request());
  }

  private CompletableFuture<String> asyncGet(Invocation.Builder target) {
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
}
