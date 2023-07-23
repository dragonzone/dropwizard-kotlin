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

import jakarta.inject.Provider;
import jakarta.ws.rs.ProcessingException;
import kotlin.Result;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlinx.coroutines.*;
import kotlinx.coroutines.slf4j.MDCContext;
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

    private static class AsyncContextContinuation implements Continuation<Object> {

        private final AsyncContext asyncContext;
        private final Continuation<? super Unit> continuation;

        public AsyncContextContinuation(AsyncContext asyncContext, Continuation<? super Unit> continuation) {
            this.asyncContext = asyncContext;
            this.continuation = continuation;
        }

        @NotNull
        @Override
        public CoroutineContext getContext() {
            return continuation.getContext();
        }

        @Override
        public void resumeWith(@NotNull Object o) {
            System.out.println("Resuming AsyncContext");
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

    private final Provider<CoroutineScope> scopeProvider;

    private final Provider<AsyncContext> asyncContextProvider;


    public CoroutineInvocationHandler(Provider<CoroutineScope> scopeProvider, Provider<AsyncContext> asyncContextProvider) {
        this.scopeProvider = scopeProvider;
        this.asyncContextProvider = asyncContextProvider;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MDCContext mdcContext = new MDCContext();
        AsyncContext asyncContext = asyncContextProvider.get();
        CoroutineScope requestCoroutineScope = scopeProvider.get();


        Deferred<Object> result = BuildersKt.async(requestCoroutineScope, mdcContext, CoroutineStart.UNDISPATCHED, (scope, continuation) -> {
            // Inject our coroutine continuation into the call arguments
            args[args.length - 1] = new AsyncContextContinuation(asyncContext, continuation);
            try {
                return method.invoke(proxy, args);
            } catch (Throwable t) {
                // Kotlin doesn't understand checked exceptions, so sneakyThrow to bypass java restrictions.
                throw sneakyThrow(t);
            }
        });
        System.out.println("Deferred: " + result);
        // Check if coroutine actually suspended
        if (result.isCompleted()) {
            return result.getCompleted();
        }
        // It did, so suspend the context
        if (!asyncContext.suspend()) {
            throw new ProcessingException(LocalizationMessages.ERROR_SUSPENDING_ASYNC_REQUEST());
        }
        return null;
    }
}
