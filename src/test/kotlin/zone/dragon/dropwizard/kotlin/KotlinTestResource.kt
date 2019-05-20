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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import kotlin.coroutines.suspendCoroutine

/**
 * @author Darth Android
 * @date 5/19/2019
 */
@Path("test")
class KotlinTestResource {

    @Inject
    internal var responseTrigger: CompletableFuture<Void>? = null

    @Inject
    internal var activeRequests: AtomicInteger? = null

    @Path("suspendedFunction")
    @GET
    @Produces("application/json")
    suspend fun getCompletionStage(request: TestRequest): TestResponse {
        val activeRequests = this.activeRequests!!.incrementAndGet()
        if (activeRequests == AsyncBundleTest.MAX_CONCURRENT) {
            responseTrigger!!.complete(null)
        }
        suspendCoroutine<Unit> {
            responseTrigger?.handle { _, error ->
                it.resumeWith(
                    if (error != null) {
                        Result.failure(error)
                    } else {
                        Result.success(Unit)
                    }
                )
            }
        }
        return TestResponse("testing", activeRequests)
    }
}
