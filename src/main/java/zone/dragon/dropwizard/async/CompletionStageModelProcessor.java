package zone.dragon.dropwizard.async;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.model.ModelProcessor;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.ResourceModel;

import javax.inject.Singleton;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Configuration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Model Processor to alter resource methods to run {@link Suspended} if they return a {@link CompletionStage} or {@link CompletableFuture}
 *
 * @author Bryan Harclerode
 */
@Slf4j
@Singleton
public class CompletionStageModelProcessor implements ModelProcessor {
    private ResourceModel processModel(ResourceModel originalModel, boolean subresource) {
        ResourceModel.Builder modelBuilder = new ResourceModel.Builder(subresource);
        for (Resource originalResource : originalModel.getResources()) {
            modelBuilder.addResource(updateResource(originalResource));
        }
        return modelBuilder.build();
    }

    @Override
    public ResourceModel processResourceModel(ResourceModel resourceModel, Configuration configuration) {
        return processModel(resourceModel, false);
    }

    @Override
    public ResourceModel processSubResource(ResourceModel subResourceModel, Configuration configuration) {
        return processModel(subResourceModel, true);
    }

    private Resource updateResource(Resource original) {
        // replace all methods on this resource, and then recursively repeat upon all child resources
        Resource.Builder resourceBuilder = Resource.builder(original);
        for (Resource childResource : original.getChildResources()) {
            resourceBuilder.replaceChildResource(childResource, updateResource(childResource));
        }
        for (ResourceMethod originalMethod : original.getResourceMethods()) {
            if (CompletionStage.class.isAssignableFrom(originalMethod.getInvocable().getRawRoutingResponseType())) {
                log.info("Marking resource method as suspended: {}", originalMethod.getInvocable().getRawRoutingResponseType());
                resourceBuilder.updateMethod(originalMethod).suspended(AsyncResponse.NO_TIMEOUT, TimeUnit.MILLISECONDS);
            }
        }
        return resourceBuilder.build();
    }
}
