package zone.dragon.dropwizard.kotlin.coroutines;

import jakarta.inject.Provider;
import jakarta.ws.rs.ProcessingException;
import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.slf4j.MDCContext;
import org.glassfish.jersey.process.internal.RequestScope;
import org.glassfish.jersey.server.AsyncContext;
import org.glassfish.jersey.server.internal.LocalizationMessages;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class CoroutineInvocationHandler implements InvocationHandler {

    /*
     * Cache reference to sentinel object since it's constant
     */
    private static final Object COROUTINE_SUSPENDED = IntrinsicsKt.getCOROUTINE_SUSPENDED();

    /*
     * Used to forcibly convert checked exceptions to unchecked exceptions
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    /*
     * Continuation that wraps Jersey's AsyncContext and handles suspending and resuming a Jersey request as
     */
    private static class AsyncContextContinuation implements Continuation<Object> {

        private final CoroutineContext coroutineContext;

        private final AsyncContext asyncContext;

        public AsyncContextContinuation(CoroutineContext coroutineContext, AsyncContext asyncContext) {
            this.coroutineContext = coroutineContext;
            this.asyncContext = asyncContext;
        }

        @NotNull
        @Override
        public CoroutineContext getContext() {
            return coroutineContext;
        }

        @Override
        public void resumeWith(@NotNull Object result) {
            // asyncContext.resume() synchronously performs IO, so we need to dispatch to Dispatchers.IO
            Dispatchers.getIO().interceptContinuation(new Continuation<>() {
                @NotNull
                @Override
                public CoroutineContext getContext() {
                    return AsyncContextContinuation.this.getContext();
                }

                @Override
                public void resumeWith(@NotNull Object dispatchedResult) {
                    if (dispatchedResult instanceof Result.Failure) {
                        asyncContext.resume(((Result.Failure) dispatchedResult).exception);
                    } else {
                        asyncContext.resume(dispatchedResult);
                    }
                }
            }).resumeWith(result);
        }

        /**
         * Suspends the Jersey request context, allowing the resource handler to return {@code null} and provide a
         * response instead via {@link AsyncContext#resume(Object)}
         */
        public void suspend() {
            if (!asyncContext.suspend()) {
                throw new ProcessingException(LocalizationMessages.ERROR_SUSPENDING_ASYNC_REQUEST());
            }
        }
    }

    private final Provider<CoroutineContext> contextProvider;

    private final Provider<AsyncContext> asyncContextProvider;

    private final RequestScope requestScope;

    public CoroutineInvocationHandler(Provider<CoroutineContext> contextProvider, Provider<AsyncContext> asyncContextProvider, RequestScope requestScope) {
        this.contextProvider = contextProvider;
        this.asyncContextProvider = asyncContextProvider;
        this.requestScope = requestScope;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        AsyncContext asyncContext = asyncContextProvider.get();
        MDCContext mdcContext = new MDCContext();
        RequestScopeContext requestScopeContext = new RequestScopeContext(requestScope);
        CoroutineContext context = contextProvider.get().plus(mdcContext).plus(requestScopeContext);
        AsyncContextContinuation asyncContextContinuation = new AsyncContextContinuation(context, asyncContext);

        Object result = CoroutineScopeKt.coroutineScope((scope, continuation) -> {
            // coroutineScope guarantees a Job element in the context
            Job coroutine = continuation.getContext().get(Job.Key);
            coroutine.invokeOnCompletion(true, true, requestScopeContext);
            // Inject our coroutine continuation into the call arguments
            args[args.length - 1] = continuation;
            try {
                return method.invoke(proxy, args);
            } catch (Throwable t) {
                // Kotlin doesn't understand checked exceptions, so sneakyThrow to bypass java restrictions.
                throw sneakyThrow(t);
            }
        }, asyncContextContinuation);
        // Check if coroutine actually suspended
        if (result == COROUTINE_SUSPENDED) {
            if (!asyncContext.suspend()) {
                throw new ProcessingException(LocalizationMessages.ERROR_SUSPENDING_ASYNC_REQUEST());
            }
            //noinspection SuspiciousInvocationHandlerImplementation
            return null;
        }
        // Coroutine didn't suspend and returned immediately
        requestScopeContext.invoke(null);
        return result;
    }
}
