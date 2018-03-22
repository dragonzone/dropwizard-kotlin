/*
 * Copyright 2018 Bryan Harclerode
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

package zone.dragon.dropwizard.async;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Configuration;

import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.glassfish.jersey.server.model.ModelProcessor;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.ResourceModel;

import com.google.common.util.concurrent.ListenableFuture;

import lombok.extern.slf4j.Slf4j;

/**
 * Model Processor to alter resource methods to run {@link Suspended} if they return a {@link ListenableFuture}, {@link CompletionStage},
 * or {@link CompletableFuture}
 *
 * @author Bryan Harclerode
 */
@Slf4j
@Singleton
public class AsyncModelProcessor implements ModelProcessor {
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

    protected boolean isAsyncType(Type type) {
        Class<?> rawType = ReflectionHelper.getRawClass(type);
        return CompletionStage.class.isAssignableFrom(rawType)
            || ListenableFuture.class.isAssignableFrom(rawType)
            || jersey.repackaged.com.google.common.util.concurrent.ListenableFuture.class.isAssignableFrom(rawType);
    }

    private Resource updateResource(Resource original) {
        // replace all methods on this resource, and then recursively repeat upon all child resources
        Resource.Builder resourceBuilder = Resource.builder(original);
        for (Resource childResource : original.getChildResources()) {
            resourceBuilder.replaceChildResource(childResource, updateResource(childResource));
        }
        for (ResourceMethod originalMethod : original.getResourceMethods()) {
            if (isAsyncType(originalMethod.getInvocable().getRawRoutingResponseType())) {
                log.info("Marking resource method as suspended: {}", originalMethod.getInvocable().getRawRoutingResponseType());
                resourceBuilder.updateMethod(originalMethod).suspended(AsyncResponse.NO_TIMEOUT, TimeUnit.MILLISECONDS);
            }
        }
        return resourceBuilder.build();
    }
}
