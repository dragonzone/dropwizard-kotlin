package zone.dragon.dropwizard.async;

import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Configures DropWizard to support returning {@link CompletionStage} and {@link CompletableFuture} from resource methods
 *
 * @author Bryan Harclerode
 * @date 9/23/2016
 */
public class CompletionStageBundle implements Bundle {
    @Override
    public void initialize(Bootstrap<?> bootstrap) { }

    @Override
    public void run(Environment environment) {
        environment.jersey().register(CompletionStageFeature.class);
    }
}
