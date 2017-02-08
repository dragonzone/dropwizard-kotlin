package zone.dragon.dropwizard.async;

import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Singleton;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Jersey {@link Feature} that enables support for resources that return {@link CompletionStage} or {@link CompletableFuture}
 *
 * @author Bryan Harclerode
 */
public class CompletionStageFeature implements Feature {
    @Override
    public boolean configure(FeatureContext context) {
        context.register(CompletionStageContainerResponseFilter.class);
        context.register(CompletionStageModelProcessor.class);
        context.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(CompletionStageInterceptionService.class).to(InterceptionService.class).in(Singleton.class);
            }
        });
        return true;
    }
}
