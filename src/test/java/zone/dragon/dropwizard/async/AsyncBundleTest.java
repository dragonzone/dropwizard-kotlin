package zone.dragon.dropwizard.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Result;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import io.dropwizard.testing.junit.DropwizardClientRule;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class AsyncBundleTest {

    /**
     * How many concurrent connections to test; Should be more than 2048 since that's the maximum number of connections Dropwizard's default
     * configuration can accept before it must start rejecting connections.
     */
    public static final int MAX_CONCURRENT = 5000;

    @Path("test")
    @Slf4j
    public static class TestResource {

        @Inject
        CompletableFuture<Void> responseTrigger;

        @Inject
        AtomicInteger activeRequests;

        @Path("completionStage")
        @GET
        public CompletionStage<Response> getCompletionStage() {
            int activeRequests = this.activeRequests.incrementAndGet();
            if (activeRequests == MAX_CONCURRENT) {
                responseTrigger.complete(null);
            }
            return responseTrigger.thenApply(ignored -> Response.status(234).build());
        }

        @Path("listenableFuture")
        @GET
        public ListenableFuture<Response> getListenableFuture() {
            int activeRequests = this.activeRequests.incrementAndGet();
            if (activeRequests == MAX_CONCURRENT) {
                responseTrigger.complete(null);
            }
            SettableFuture<Response> promise = SettableFuture.create();
            responseTrigger.thenAccept(ignored -> promise.set(Response.status(234).build()));
            return promise;
        }

        @Path("repackagedListenableFuture")
        @GET
        public jersey.repackaged.com.google.common.util.concurrent.ListenableFuture<Response> getRepackagedListenableFuture() {
            int activeRequests = this.activeRequests.incrementAndGet();
            if (activeRequests == MAX_CONCURRENT) {
                responseTrigger.complete(null);
            }
            jersey.repackaged.com.google.common.util.concurrent.SettableFuture<Response> promise = jersey.repackaged.com.google.common.util.concurrent.SettableFuture
                .create();
            responseTrigger.thenAccept(ignored -> promise.set(Response.status(234).build()));
            return promise;
        }
    }

    @Rule
    public final DropwizardClientRule dropwizard = new DropwizardClientRule(
        AsyncFeature.class,
        TestResource.class,
        new AbstractBinder() {
            @Override
            protected void configure() {
                bind(new CompletableFuture<Void>()).to(new TypeLiteral<CompletableFuture<Void>>() {});
                bind(new AtomicInteger()).to(AtomicInteger.class);
            }
        }
    );
    private HttpClient client;

    @Before
    public void setup() throws Exception {
        client = new HttpClient();
        client.setMaxConnectionsPerDestination(10000);
        client.start();
    }

    @After
    public void tearDown() throws Exception {
        client.stop();
    }

    private void testEndpoint(String endpoint) throws InterruptedException, ExecutionException, TimeoutException {
        long startTime = System.currentTimeMillis();
        final CompletableFuture[] promises = new CompletableFuture[MAX_CONCURRENT];
        for (int i = 0; i < MAX_CONCURRENT; i++) {
            CompletableFuture<Result> promise = new CompletableFuture<>();
            client.newRequest(dropwizard.baseUri() + "/test/" + endpoint).send(result -> {
                if (result.getFailure() != null) {
                    promise.completeExceptionally(result.getFailure());
                } else {
                    promise.complete(result);
                }
            });
            promises[i] = promise.thenAccept(result -> assertThat(result.getResponse().getStatus()).isEqualTo(234));
            // If requests are made faster than Dropwizard can spin up threads and hand off requests, it will start rejecting them
            // Sleep periodically to let it catch up
            if (i % 100 == 0) {
                Thread.sleep(100);
            }
        }
        log.info("Requests sent");

        CompletableFuture.allOf(promises).get(60, TimeUnit.SECONDS);
    }

    @Test
    public void testCompletionStage() throws InterruptedException, ExecutionException, TimeoutException {
        testEndpoint("completionStage");
    }

    @Test
    public void testListenableFuture() throws InterruptedException, ExecutionException, TimeoutException {
        testEndpoint("listenableFuture");
    }

    @Test
    public void testRepackagedListenableFuture() throws InterruptedException, ExecutionException, TimeoutException {
        testEndpoint("repackagedListenableFuture");
    }
}
