package zone.dragon.dropwizard.async;

import com.google.common.collect.ImmutableList;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.jersey.server.internal.JerseyResourceContext;
import org.glassfish.jersey.server.internal.process.AsyncContext;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Method interceptor which wraps resource calls that return {@link CompletionStage} or {@link CompletableFuture} and resumes them when the
 * {@code CompletionStage} settles
 *
 * @author Bryan Harclerode
 */
@Singleton
public class CompletionStageInterceptionService implements InterceptionService {
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
            CompletionStage<?> promise = (java.util.concurrent.CompletionStage<?>) invocation.proceed();
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

    private final List<MethodInterceptor> interceptor;
    private final JerseyResourceContext   context;

    @Inject
    public CompletionStageInterceptionService(Provider<AsyncContext> asyncResponseProvider, JerseyResourceContext context) {
        this.context = context;
        interceptor = ImmutableList.of(new CompletionStageMethodInterceptor(asyncResponseProvider));
    }

    @Override
    public Filter getDescriptorFilter() {
        return descriptor -> true;
    }

    @Override
    public List<MethodInterceptor> getMethodInterceptors(Method method) {
        if (CompletionStage.class.isAssignableFrom(method.getReturnType()) && isResourceMethod(method)) {
            return interceptor;
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
