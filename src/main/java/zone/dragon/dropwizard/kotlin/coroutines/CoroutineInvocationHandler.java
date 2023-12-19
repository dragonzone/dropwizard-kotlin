/*
 * MIT License
 *
 * Copyright (c) 2019-2023 Bryan Harclerode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package zone.dragon.dropwizard.kotlin.coroutines;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import jakarta.inject.Provider;
import jakarta.ws.rs.ProcessingException;
import kotlin.Result;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.slf4j.MDCContext;
import org.glassfish.jersey.server.AsyncContext;
import org.glassfish.jersey.server.internal.LocalizationMessages;
import zone.dragon.dropwizard.kotlin.coroutines.scopes.RequestCoroutineScope;

/**
 * {@link InvocationHandler} that bridges the gap between Jersey and Coroutines
 * <p/>
 * This is written in java because it's easier to build and launch coroutines from java than it is to make
 * compiler-generated suspension points play nice with Jersey. Most of the Kotlin APIs needed to access the raw control
 * mechanisms used to emulate the compiler intrinsics are internal or not stable, but the intrinsics themselves are and
 * we can just access them from Java directly.
 */
public class CoroutineInvocationHandler implements InvocationHandler {
    /*
     * Used to forcibly convert checked exceptions to unchecked exceptions
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    /**
     * Continuation which resumes the Jersey {@link AsyncContext} when called
     */
    private static class AsyncContextContinuation implements Continuation<Object> {

        private final AsyncContext asyncContext;
        private final Continuation<? super Unit> continuation;

        public AsyncContextContinuation(AsyncContext asyncContext, Continuation<? super Unit> continuation) {
            this.asyncContext = asyncContext;
            this.continuation = continuation;
        }

        @Override
        public CoroutineContext getContext() {
            return continuation.getContext();
        }

        @Override
        public void resumeWith(Object o) {
            // Switch dispatchers to handle blocking since asyncContext.resume will block
            // This won't actually result in a thread switch if the handler is still running in Dispatchers.Default
            BuildersKt.<Unit>withContext(Dispatchers.getIO(), (scope, continuation) -> {
                if (o instanceof Result.Failure) {
                    asyncContext.resume(((Result.Failure) o).exception);
                } else {
                    asyncContext.resume(o);
                }
                return Unit.INSTANCE;
            }, continuation);
        }
    }

    /**
     * Provides the {@link CoroutineScope} which should be used to invoke this coroutine; Typically this is a
     * {@link RequestCoroutineScope}
     */
    private final Provider<CoroutineScope> scopeProvider;

    /**
     * Provides the {@link AsyncContext} for the current jersey request, which will be used to return the result from
     * this coroutine
     */
    private final Provider<AsyncContext> asyncContextProvider;

    public CoroutineInvocationHandler(Provider<CoroutineScope> scopeProvider, Provider<AsyncContext> asyncContextProvider) {
        this.scopeProvider = scopeProvider;
        this.asyncContextProvider = asyncContextProvider;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        AsyncContext asyncContext = asyncContextProvider.get();
        CoroutineScope requestCoroutineScope = scopeProvider.get();
        // Snapshot MDC here to ensure it's preserved for the coroutine and the response filters if the coroutine
        // suspends and resumes
        MDCContext mdcContext = new MDCContext();

        // Start UNDISPATCHED to run as long as possible on the current request thread
        // BuildersKt.async will handle initializing the thread context elements on the current thread without dispatching
        Deferred<Object> result = BuildersKt.async(requestCoroutineScope, mdcContext, CoroutineStart.UNDISPATCHED, (scope, continuation) -> {
            // Inject our coroutine continuation into the call arguments
            // We don't really care about the TERMINATING_CONTINUATION from ContinuationValueParamProvider that is being
            // overwritten here.
            // This InvocationHandler is only returned by CoroutineInvocationHandlerProvider for coroutines, so we are
            // guaranteed to have at least 1 parameter, which must be at the end of the argument list and accept a
            // Continuation
            args[args.length - 1] = new AsyncContextContinuation(asyncContext, continuation);
            try {
                return method.invoke(proxy, args);
            } catch (Throwable t) {
                // Kotlin doesn't understand checked exceptions, so sneakyThrow to bypass java restrictions.
                throw sneakyThrow(t);
            }
        });
        // Check if coroutine actually suspended
        if (result.isCompleted()) {
            // The coroutine never actually suspended, and thus, AsyncContextContinuation was never called.
            // Instead, synchronously grab the result, and return it like a normal Jersey handler
            return result.getCompleted();
        }
        // The coroutine did suspend, so suspend the Jersey context and let AsyncContextContinuation handle the resume
        if (!asyncContext.suspend()) {
            throw new ProcessingException(LocalizationMessages.ERROR_SUSPENDING_ASYNC_REQUEST());
        }
        // Async resource handlers are supposed to be void / return null
        return null;
    }
}
