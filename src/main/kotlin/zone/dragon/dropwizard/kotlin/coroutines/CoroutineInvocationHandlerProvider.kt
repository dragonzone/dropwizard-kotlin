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

package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.process.internal.RequestScope
import org.glassfish.jersey.server.AsyncContext
import org.glassfish.jersey.server.model.Invocable
import org.glassfish.jersey.server.spi.internal.ResourceMethodInvocationHandlerProvider
import java.lang.reflect.InvocationHandler
import kotlin.reflect.jvm.kotlinFunction

/**
 * Interceptor that intercepts resource method invocation for suspending kotlin functions and suspends the jersey
 * processing context when the coroutine suspends and dispatches to a different thread
 */
class CoroutineInvocationHandlerProvider @Inject constructor(
    asyncContext: Provider<AsyncContext>, scope: Provider<CoroutineScope>
) : ResourceMethodInvocationHandlerProvider {

    /**
     * Binder for configuring [CoroutineInvocationHandlerProvider] to intercept resource handlers that are coroutines
     */
    class Binder : AbstractBinder() {
        override fun configure() {
            bind(CoroutineInvocationHandlerProvider::class.java)
                .to(ResourceMethodInvocationHandlerProvider::class.java)
                .`in`(Singleton::class.java)
        }
    }

    private var invocationHandler = CoroutineInvocationHandler(scope, asyncContext)

    override fun create(method: Invocable): InvocationHandler? {
        if (method.handlingMethod.kotlinFunction?.isSuspend == true) {
            return invocationHandler
        }
        return null
    }
}
