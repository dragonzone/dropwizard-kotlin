/*
 * MIT License
 *
 * Copyright (c) 2023 Bryan Harclerode
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
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.process.internal.RequestScoped
import org.glassfish.jersey.server.ContainerRequest
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import org.glassfish.hk2.api.Factory as HK2Factory

/**
 * [Job] representing a Jersey request
 *
 * @author Bryan Harclerode
 * @date 12/28/2023
 */
class RequestJob private constructor(private val job: CompletableJob) : Job by job {

    companion object {
        /**
         * Name of the request property that holds the [RequestJob]
         *
         * This is used by [Listener] to complete the job after the request has finished processing.
         */
        private const val REQUEST_JOB_NAME = "zone.dragon.dropwizard.kotlin.REQUEST_JOB"
    }

    class Listener : ApplicationEventListener, RequestEventListener {
        override fun onEvent(event: ApplicationEvent?) = Unit

        override fun onRequest(requestEvent: RequestEvent): RequestEventListener? {
            if (requestEvent.type == RequestEvent.Type.START) {
                return this
            }
            return null
        }

        override fun onEvent(event: RequestEvent) {
            if (event.type == RequestEvent.Type.FINISHED) {
                (event.containerRequest.getProperty(REQUEST_JOB_NAME) as? CompletableJob)?.complete()
            }
        }
    }

    class Factory @Inject constructor(private val applicationJob: ApplicationJob, private val request: Provider<ContainerRequest>) : HK2Factory<RequestJob> {
        override fun provide(): RequestJob {
            val job = Job(applicationJob)
            request.get().setProperty(REQUEST_JOB_NAME, job)
            return RequestJob(job)
        }

        override fun dispose(instance: RequestJob?) = Unit
    }

    class Binder : AbstractBinder() {
        override fun configure() {
            bind(Listener::class.java).to(ApplicationEventListener::class.java).`in`(Singleton::class.java)
            bindFactory(Factory::class.java, Singleton::class.java)
                .to(RequestJob::class.java)
                .to(Job::class.java)
                .`in`(RequestScoped::class.java)
                .ranked(-1)

        }
    }
}
