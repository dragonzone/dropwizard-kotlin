package zone.dragon.dropwizard.async;

import com.google.common.collect.ImmutableList;
import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.jersey.server.internal.JerseyResourceContext;
import org.glassfish.jersey.server.internal.process.AsyncContext;
import org.glassfish.jersey.server.model.Resource;

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
 * @date 10/5/2016
 */
@Singleton
public class CompletionStageInterceptionService implements InterceptionService {
    /**
     * Interceptor used to resume an asynchronous response after the {@link CompletionStage} returned by the resource method has settled
     */
    private static class CompletionStageMethodInterceptor implements MethodInterceptor {
        private final Provider<AsyncContext> asyncResponseProvider;

        public CompletionStageMethodInterceptor(Provider<AsyncContext> asyncResponseProvider) {
            this.asyncResponseProvider = asyncResponseProvider;
        }

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
        return this::isJerseyResource;
    }

    @Override
    public List<MethodInterceptor> getMethodInterceptors(Method method) {
        if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            return interceptor;
        }
        return null;
    }

    @Override
    public List<ConstructorInterceptor> getConstructorInterceptors(Constructor<?> constructor) {
        return null;
    }

    private boolean isJerseyResource(Descriptor descriptor) {
        if (context.getResourceModel() == null) {
            return false;
        }
        for (Resource resource : context.getResourceModel().getResources()) {
            for (Class<?> clazz : resource.getHandlerClasses()) {
                if (descriptor.getImplementation().equals(clazz.getCanonicalName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
