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

import java.lang.reflect.InvocationHandler;
import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.container.AsyncResponse;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.server.internal.inject.ConfiguredValidator;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.spi.internal.ParamValueFactoryWithSource;
import org.glassfish.jersey.server.spi.internal.ParameterValueHelper;
import org.glassfish.jersey.server.spi.internal.ResourceMethodDispatcher;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Provider that detects asynchronous resource methods and returns an {@link AsyncDispatcher} to handle them
 *
 * @author Bryan Harclerode
 */
public class AsyncJavaResourceMethodDispatcherProvider implements ResourceMethodDispatcher.Provider {

    private final ServiceLocator serviceLocator;

    private final Provider<AsyncResponse> responseProvider;

    @Inject
    public AsyncJavaResourceMethodDispatcherProvider(ServiceLocator serviceLocator, Provider<AsyncResponse> responseProvider) {
        this.serviceLocator = serviceLocator;
        this.responseProvider = responseProvider;
    }

    @Override
    public ResourceMethodDispatcher create(
        Invocable resourceMethod, InvocationHandler invocationHandler, ConfiguredValidator responseValidator
    ) {
        List<ParamValueFactoryWithSource<?>> valueProviders = ParameterValueHelper.createValueProviders(serviceLocator, resourceMethod);
        Class<?> returnType = resourceMethod.getHandlingMethod().getReturnType();

        if (CompletionStage.class.isAssignableFrom(returnType)
            || ListenableFuture.class.isAssignableFrom(returnType)
            || jersey.repackaged.com.google.common.util.concurrent.ListenableFuture.class.isAssignableFrom(returnType)) {
            return new AsyncDispatcher(resourceMethod, invocationHandler, valueProviders, responseValidator, responseProvider);
        } else {
            return null;
        }
    }
}
