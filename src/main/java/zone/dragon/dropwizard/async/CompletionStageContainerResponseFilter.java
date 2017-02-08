package zone.dragon.dropwizard.async;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerResponse;

import javax.annotation.Priority;
import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.concurrent.CompletionStage;

/**
 * Filter that replaces the {@code CompletionStage<T>} entity type with just {@code T} to match the return type of the resource.
 *
 * @author Bryan Harclerode
 */
@Slf4j
@Singleton
@Priority(Integer.MAX_VALUE)
public class CompletionStageContainerResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        if (!responseContext.hasEntity() || !(responseContext.getEntityType() instanceof ParameterizedType)) {
            return;
        }
        ParameterizedType entityType = (ParameterizedType) responseContext.getEntityType();
        if (!CompletionStage.class.isAssignableFrom((Class<?>) entityType.getRawType())) {
            return;
        }
        // Interface doesn't support changing of the entity type, so check for the jersey implementation
        if (!(responseContext instanceof ContainerResponse)) {
            log.warn("response context wasn't a ContainerResponse");
            return;
        }
        ContainerResponse response = (ContainerResponse) responseContext;
        response.setEntityType(entityType.getActualTypeArguments()[0]);
    }
}