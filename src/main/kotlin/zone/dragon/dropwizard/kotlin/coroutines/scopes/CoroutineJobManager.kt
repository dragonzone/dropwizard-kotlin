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
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import mu.KLogging
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener

/**
 * Application listener that creates and completes a [Job] for each request, which can be further used by coroutines for structured
 * concurrency.
 *
 * This is used by [RequestCoroutineScope] to set up the coroutine scope for each request.
 */
class CoroutineJobManager @Inject constructor(private val applicationJob: ApplicationJob) : ApplicationEventListener {

    companion object : KLogging() {
        /**
         * Name of the request property that holds the [RequestScopeJob]
         *
         * [RequestCoroutineScope] looks for the request job here to construct the coroutine context for the request
         */
        internal const val REQUEST_JOB_NAME = "zone.dragon.dropwizard.kotlin.REQUEST_JOB"
    }

    override fun onEvent(event: ApplicationEvent) = Unit

    override fun onRequest(requestEvent: RequestEvent): RequestEventListener? {
        if (requestEvent.type == RequestEvent.Type.START) {
            val requestJob = RequestScopeJob(applicationJob)
            requestEvent.containerRequest.setProperty(REQUEST_JOB_NAME, requestJob)
            return requestJob
        }
        return null
    }

    /**
     * [Job] representing a Jersey request
     */
    private class RequestScopeJob(
        parentJob: Job, private val requestJob: CompletableJob = Job(parentJob)
    ) : RequestEventListener, Job by requestJob {

        override fun onEvent(event: RequestEvent) {
            if (event.type == RequestEvent.Type.FINISHED) {
                requestJob.complete()
            }
        }
    }
}
