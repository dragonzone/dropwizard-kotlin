package zone.dragon.dropwizard.async;

import io.dropwizard.testing.junit.DropwizardClientRule;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Bryan Harclerode
 * @date 9/23/2016
 */
@Slf4j
public class AsyncBundleTest {
    @ClassRule
    public static final DropwizardClientRule RULE = new DropwizardClientRule(CompletionStageFeature.class, TestResource.class);
    private static WebTarget client;

    /**
     * @author Darth Android
     * @date 10/5/2016
     */
    @Path("test")
    @Slf4j
    public static class TestResource {
        @Path("stage")
        @GET
        public CompletionStage<String> getString() {
            CompletableFuture<String> promise = new CompletableFuture<>();
            TestResource.log.info("  Received");
            new Thread(() -> {
                TestResource.log.info("    Offloaded!");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //TODO Handle it!
                }
                TestResource.log.info("        Completed!");
                promise.complete("Hello World");
            }).start();
            return promise;
        }
    }

    @Before
    public void setup() {
        client = ClientBuilder.newClient().target(RULE.baseUri());
    }

    @Test
    public void test() throws InterruptedException, ExecutionException, TimeoutException {
        long startTime = System.currentTimeMillis();
        log.info(RULE.baseUri().toASCIIString());
        final CompletableFuture[] promises = new CompletableFuture[100];
        for (int i = 0; i < 100; i++) {
            CompletableFuture promise = promises[i] = new CompletableFuture();
            new Thread(() -> {
                log.info("Request");
                assertEquals("Hello World", client.path("/test/stage").request("application/json").buildGet().invoke(String.class));
                log.info("Responded");
                promise.complete(new Object());
            }).start();
        }
        CompletableFuture.allOf(promises).get(10000, TimeUnit.MILLISECONDS);
        assertTrue(System.currentTimeMillis() - startTime < 10000);
    }
}
