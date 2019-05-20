/*
 * Copyright 2019 Bryan Harclerode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package zone.dragon.dropwizard.kotlin;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@ExtendWith(DropwizardExtensionsSupport.class)
public class AsyncBundleTest {

    /**
     * How many concurrent connections to test; Should be more than 2048 since that's the maximum number of connections Dropwizard's default
     * configuration can accept before it must start rejecting connections.
     */
    public static final int MAX_CONCURRENT = 500;

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
            if (activeRequests % 100 == 0) {
                log.info("Reached {} active requests", activeRequests);
            }
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

    public final DropwizardClientExtension dropwizard = new DropwizardClientExtension(
        KotlinFeature.class,
        KotlinTestResource.class,
        KotlinTestResource.class,
        new AbstractBinder() {
            @Override
            protected void configure() {
                bind(new CompletableFuture<Void>()).to(new TypeLiteral<CompletableFuture<Void>>() {});
                bind(new AtomicInteger()).to(AtomicInteger.class);
            }
        }
    );
    private HttpClient client;

    @BeforeEach
    public void setup() throws Exception {
        client = new HttpClient();
        client.setMaxConnectionsPerDestination(10000);
        client.start();


    }

    @AfterEach
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
    public void testSuspendedFuction() throws InterruptedException, ExecutionException, TimeoutException {
        testEndpoint("suspendedFunction");
    }

    @Test
    @Disabled
    public void testCompletionStage() throws InterruptedException, ExecutionException, TimeoutException {
        testEndpoint("completionStage");
    }

    @Test
    @Disabled
    public void testListenableFuture() throws InterruptedException, ExecutionException, TimeoutException {
        testEndpoint("listenableFuture");
    }

    @Test
    @Disabled
    public void testRepackagedListenableFuture() throws InterruptedException, ExecutionException, TimeoutException {
        testEndpoint("repackagedListenableFuture");
    }
}
