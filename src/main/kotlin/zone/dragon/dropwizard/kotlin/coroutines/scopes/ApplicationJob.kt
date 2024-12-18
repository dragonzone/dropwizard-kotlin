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

import jakarta.inject.Singleton
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import io.github.oshai.kotlinlogging.KotlinLogging
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.hk2.api.Factory as HK2Factory

private val logger = KotlinLogging.logger {}

/**
 * [Job] representing a Jersey Application
 *
 * This is used as a parent supervisor of all coroutines created by Jersey to handle requests and ensure proper structured concurrency.
 *
 * @author Bryan Harclerode
 */
class ApplicationJob private constructor(private val job: CompletableJob) : Job by job {

    constructor() : this(SupervisorJob())

    /**
     * HK2 Factory for creating [ApplicationJob]s
     */
    class Factory : HK2Factory<ApplicationJob> {
        override fun provide(): ApplicationJob {
            logger.info { "Application coroutine context initialized" }
            return ApplicationJob()
        }

        override fun dispose(instance: ApplicationJob) {
            instance.job.cancel("Application is shutting down")

            if (!instance.job.isCompleted) {
                logger.info { "Waiting for coroutines to terminate..." }
                runBlocking {
                    instance.job.join()
                }
                logger.info { "Coroutines stopped" }
            }
        }

    }

    /**
     * HK2 Binder for enabling creation and shutdown of an [ApplicationJob]
     */
    class Binder : AbstractBinder() {
        override fun configure() {
            bindFactory(Factory::class.java, Singleton::class.java)
                .to(ApplicationJob::class.java)
                .to(Job::class.java)
                .`in`(Singleton::class.java)
                .ranked(-2)
        }
    }
}
