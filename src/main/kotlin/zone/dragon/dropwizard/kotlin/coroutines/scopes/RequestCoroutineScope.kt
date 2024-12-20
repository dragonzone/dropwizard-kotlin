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
package zone.dragon.dropwizard.kotlin.coroutines.scopes

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ThreadContextElement
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.process.internal.RequestContext
import org.glassfish.jersey.process.internal.RequestScope
import org.glassfish.jersey.process.internal.RequestScoped
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import org.glassfish.hk2.api.Factory as HK2Factory

class RequestCoroutineScope private constructor(
    /**
     * Context for this request, without the job
     */
    parentContext: CoroutineContext,
    private val requestJob: Job
) : CoroutineScope by CoroutineScope(parentContext + requestJob) {

    companion object Key : CoroutineContext.Key<Factory.RequestScopeContextElement> {
        const val NAME = "zone.dragon.dropwizard.kotlin.REQUEST_SCOPE"
    }

    class Factory @Inject constructor(
        private val applicationScope: ApplicationCoroutineScope,
        private val requestScope: RequestScope,
        private val requestJob: Provider<Job>
    ) : HK2Factory<RequestCoroutineScope> {

        companion object {
            private val currentRequestContextField: Field

            init {
                try {
                    currentRequestContextField = RequestScope::class.java.getDeclaredField("currentRequestContext")
                    currentRequestContextField.isAccessible = true
                } catch (e: NoSuchFieldException) {
                    throw RuntimeException(
                        "This version of dropwizard-kotlin is not compatible with the version of jersey in use", e
                    )
                }
            }
        }

        private val threadLocal = currentRequestContextField.get(requestScope) as ThreadLocal<RequestContext?>

        override fun provide(): RequestCoroutineScope {
            val requestJob = requestJob.get()
            val requestScopeContextElement = RequestScopeContextElement(requestJob)
            return RequestCoroutineScope(applicationScope.coroutineContext + requestScopeContextElement, requestJob)
        }

        override fun dispose(instance: RequestCoroutineScope) = Unit

        inner class RequestScopeContextElement(job: Job) : ThreadContextElement<RequestContext?>,
                                                           AbstractCoroutineContextElement(Key),
                                                           CompletionHandler {

            init {
                job.invokeOnCompletion(this)
            }

            private val requestContext = requestScope.suspendCurrent() ?: null
            private val active = AtomicBoolean(true)
            override fun restoreThreadContext(context: CoroutineContext, oldState: RequestContext?) {
                threadLocal.set(oldState)
            }

            override fun updateThreadContext(context: CoroutineContext): RequestContext? {
                val oldScope = threadLocal.get()
                threadLocal.set(requestContext)
                return oldScope
            }

            override fun invoke(cause: Throwable?) {
                if (active.compareAndSet(true, false)) {
                    requestContext?.release()
                }
            }
        }
    }

    /**
     * HK2 Binder that configures the [RequestCoroutineScope] and it's necessary components in Jersey
     */
    class Binder : AbstractBinder() {
        override fun configure() {
            bindFactory(Factory::class.java, Singleton::class.java)
                .to(RequestCoroutineScope::class.java)
                .to(CoroutineScope::class.java)
                .`in`(RequestScoped::class.java)
                .named(NAME)
                .ranked(-1)
        }
    }
}
