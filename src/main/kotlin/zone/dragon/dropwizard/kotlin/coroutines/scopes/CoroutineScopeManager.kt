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
import jakarta.inject.Named
import jakarta.inject.Provider
import kotlinx.coroutines.CoroutineScope
import mu.KLogging
import org.glassfish.hk2.api.IterableProvider
import org.glassfish.hk2.api.ServiceHandle
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import java.util.concurrent.atomic.AtomicReference

/**
 * Application listener that injects and disposes the [ApplicationCoroutineScope] for the Jersey container and
 * [RequestCoroutineScope] for each request
 */
class CoroutineScopeManager @Inject constructor(
    @param:Named(ApplicationCoroutineScope.NAME) private val applicationScopeProvider: IterableProvider<CoroutineScope>,
    @param:Named(RequestCoroutineScope.NAME) private val requestScopeProvider: IterableProvider<RequestCoroutineScope>
) : ApplicationEventListener {

    companion object : KLogging()

    /**
     * Tracks the currently active application scope
     */
    private val activeApplicationScope = AtomicReference<ServiceHandle<CoroutineScope>?>(null)

    override fun onEvent(event: ApplicationEvent) {
        when (event.type) {
            // Obtain and save a handle if a scope isn't already active
            ApplicationEvent.Type.INITIALIZATION_START -> {
                val handle = applicationScopeProvider.handle
                if (activeApplicationScope.compareAndSet(null, handle)) {
                    handle.service
                }
            }
            // Destroy the handle when the application closes if a scope is active
            ApplicationEvent.Type.DESTROY_FINISHED -> {
                activeApplicationScope.getAndSet(null)?.close()
            }
            // Ignore other events
            else -> {}
        }
    }

    override fun onRequest(requestEvent: RequestEvent): RequestEventListener? {
        if (requestEvent.type == RequestEvent.Type.START) {
            // RequestScope is not yet available, so we have to pass the provider and load it only on the appropriate
            // event
            return RequestScopeListener(requestScopeProvider)
        }
        return null
    }

    /**
     * Request Listener that creates and completes a [CoroutineScope] for each request
     */
    private class RequestScopeListener(
        private val requestScopeProvider: Provider<RequestCoroutineScope>
    ) : RequestEventListener {

        private lateinit var requestScope: RequestCoroutineScope

        override fun onEvent(event: RequestEvent) {
            when (event.type) {
                // Since we can't capture the RequestScope in the START event of the ApplicationEventListener,
                // we instead have to wait until the MATCHING_START event
                RequestEvent.Type.MATCHING_START -> {
                    requestScope = requestScopeProvider.get()
                }

                RequestEvent.Type.FINISHED -> {
                    requestScope.complete()
                }
                // Ignore other events
                else -> {}
            }
        }
    }
}
