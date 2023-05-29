/*
 * Copyright 2019 Bryan Harclerode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package zone.dragon.dropwizard.kotlin

import jakarta.inject.Singleton
import jakarta.ws.rs.core.Feature
import jakarta.ws.rs.core.FeatureContext
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.internal.inject.Binder
import org.glassfish.jersey.server.spi.internal.ResourceMethodInvocationHandlerProvider
import zone.dragon.dropwizard.kotlin.coroutines.ApplicationJobManager
import zone.dragon.dropwizard.kotlin.coroutines.ContinuationValueParamProvider
import zone.dragon.dropwizard.kotlin.coroutines.CoroutineInvocationHandlerProvider
import zone.dragon.dropwizard.kotlin.coroutines.CoroutineModelProcessor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Jersey [Feature] that enables support for resources that return [CompletionStage] or [CompletableFuture]
 *
 * @author Bryan Harclerode
 */
class KotlinFeature : Feature {
    override fun configure(context: FeatureContext): Boolean {
        context.register(CoroutineModelProcessor::class.java)
        context.register(ApplicationJobManager::class.java)
        context.register(ContinuationValueParamProvider::class.java)
        context.register(object : AbstractBinder() {
            override fun configure() {
                bind(CoroutineInvocationHandlerProvider::class.java)
                    .to(ResourceMethodInvocationHandlerProvider::class.java)
                    .`in`(
                        Singleton::class.java
                    )
            }

        })
        return true
    }
}
