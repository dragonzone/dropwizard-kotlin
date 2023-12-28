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

import jakarta.ws.rs.core.Feature
import jakarta.ws.rs.core.FeatureContext
import zone.dragon.dropwizard.kotlin.coroutines.scopes.ApplicationCoroutineScope
import zone.dragon.dropwizard.kotlin.coroutines.scopes.ApplicationJob
import zone.dragon.dropwizard.kotlin.coroutines.scopes.CoroutineJobManager
import zone.dragon.dropwizard.kotlin.coroutines.scopes.RequestCoroutineScope

/**
 * Jersey [Feature] that enables support for resources that are handled by suspending kotlin functions
 *
 * @author Bryan Harclerode
 */
class KotlinCoroutineFeature : Feature {
    override fun configure(context: FeatureContext): Boolean {
        // Model adjustments
        context.register(CoroutineModelProcessor::class.java)
        // Application and Request Scope
        context.register(ApplicationCoroutineScope.Binder::class.java)
        context.register(RequestCoroutineScope.Binder::class.java)
        context.register(ApplicationJob.Binder::class.java)
        context.register(CoroutineJobManager::class.java)
        // Resource Handler Invocation
        context.register(ContinuationValueParamProvider::class.java)
        context.register(CoroutineInvocationHandlerProvider.Binder::class.java)
        context.register(JettyCoroutineDispatcherFactory.Binder())

        return true
    }
}
