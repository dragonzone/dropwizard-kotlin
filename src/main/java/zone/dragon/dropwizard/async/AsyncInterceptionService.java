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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.jersey.server.internal.JerseyResourceContext;
import org.glassfish.jersey.server.internal.process.AsyncContext;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Method interceptor which wraps resource calls that return {@link CompletableFuture} or {@link ListenableFuture} and resumes them when the
 * future settles
 *
 * @author Bryan Harclerode
 */
@Singleton
public class AsyncInterceptionService implements InterceptionService {
    /**
     * Interceptor used to resume an asynchronous response after the {@link CompletionStage} returned by the resource method has settled
     */
    @RequiredArgsConstructor
    private static class CompletionStageMethodInterceptor implements MethodInterceptor {
        @NonNull
        private final Provider<AsyncContext> asyncResponseProvider;

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            AsyncContext response = asyncResponseProvider.get();
            response.suspend();
            CompletionStage<?> promise = (CompletionStage<?>) invocation.proceed();
            promise.whenComplete((result, error) -> {
                if (error != null) {
                    response.resume(error);
                } else {
                    response.resume(result);
                }
            });
            return null;
        }
    }

    /**
     * Interceptor used to resume an asynchronous response after the {@link ListenableFuture} returned by the resource method has settled
     */
    @RequiredArgsConstructor
    private static class ListenableFutureMethodInterceptor implements MethodInterceptor {
        @NonNull
        private final Provider<AsyncContext> asyncResponseProvider;

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            AsyncContext response = asyncResponseProvider.get();
            response.suspend();
            ListenableFuture<?> promise = (ListenableFuture<?>) invocation.proceed();
            promise.addListener(() -> {
                if (promise.isDone()) {
                    try {
                        response.resume(promise.get());
                    } catch (ExecutionException e) {
                        response.resume(e.getCause());
                    } catch (Throwable t) {
                        response.resume(t);
                    }
                }
            }, MoreExecutors.directExecutor()); //TODO move to different thread pool so we don't block the resolving thread
            return null;
        }
    }

    /**
     * Interceptor used to resume an asynchronous response after the {@link ListenableFuture} returned by the resource method has settled
     */
    @RequiredArgsConstructor
    private static class RepackagedListenableFutureMethodInterceptor implements MethodInterceptor {
        @NonNull
        private final Provider<AsyncContext> asyncResponseProvider;

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            AsyncContext response = asyncResponseProvider.get();
            response.suspend();
            jersey.repackaged.com.google.common.util.concurrent.ListenableFuture<?> promise = (jersey.repackaged.com.google.common.util.concurrent.ListenableFuture<?>) invocation
                .proceed();
            promise.addListener(() -> {
                if (promise.isDone()) {
                    try {
                        response.resume(promise.get());
                    } catch (ExecutionException e) {
                        response.resume(e.getCause());
                    } catch (Throwable t) {
                        response.resume(t);
                    }
                }
            }, MoreExecutors.directExecutor()); //TODO move to different thread pool so we don't block the resolving thread
            return null;
        }
    }

    private final List<MethodInterceptor> completionStageInterceptor;
    private final List<MethodInterceptor> listenableFutureInterceptor;
    private final List<MethodInterceptor> repackagedListenableFutureInterceptor;
    private final JerseyResourceContext context;

    @Inject
    public AsyncInterceptionService(Provider<AsyncContext> asyncResponseProvider, JerseyResourceContext context) {
        this.context = context;
        completionStageInterceptor = ImmutableList.of(new CompletionStageMethodInterceptor(asyncResponseProvider));
        listenableFutureInterceptor = ImmutableList.of(new ListenableFutureMethodInterceptor(asyncResponseProvider));
        repackagedListenableFutureInterceptor = ImmutableList.of(new RepackagedListenableFutureMethodInterceptor(asyncResponseProvider));
    }

    @Override
    public Filter getDescriptorFilter() {
        return descriptor -> true;
    }

    @Override
    public List<MethodInterceptor> getMethodInterceptors(Method method) {
        if (isResourceMethod(method)) {
            if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
                return completionStageInterceptor;
            }
            if (ListenableFuture.class.isAssignableFrom(method.getReturnType())) {
                return listenableFutureInterceptor;
            }
            if (jersey.repackaged.com.google.common.util.concurrent.ListenableFuture.class.isAssignableFrom(method.getReturnType())) {
                return repackagedListenableFutureInterceptor;
            }
        }
        return null;
    }

    @Override
    public List<ConstructorInterceptor> getConstructorInterceptors(Constructor<?> constructor) {
        return null;
    }

    private boolean isResourceMethod(Method method) {
        if (context.getResourceModel() == null) {
            return false;
        }
        for (Resource resource : context.getResourceModel().getResources()) {
            if (isResourceMethod(method, resource)) {
                return true;
            }
        }
        return false;
    }

    private boolean isResourceMethod(Method method, Resource resource) {
        for (ResourceMethod resourceMethod : resource.getResourceMethods()) {
            if (resourceMethod.getInvocable().getDefinitionMethod().equals(method) || resourceMethod
                .getInvocable()
                .getHandlingMethod()
                .equals(method)) {
                return true;
            }
        }
        for (Resource childResource : resource.getChildResources()) {
            if (isResourceMethod(method, childResource)) {
                return true;
            }
        }
        return false;
    }
}
