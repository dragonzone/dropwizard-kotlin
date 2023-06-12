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

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.dropwizard.core.Configuration
import io.dropwizard.core.ConfiguredBundle
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import zone.dragon.dropwizard.kotlin.coroutines.KotlinCoroutineFeature

/**
 * Configures Dropwizard to support suspendable coroutines and enables support for serializing to/from kotlin classes
 * using Jackson.
 *
 * @author Bryan Harclerode
 */
class KotlinBundle : ConfiguredBundle<Configuration> {
    override fun initialize(bootstrap: Bootstrap<*>) {
        bootstrap.objectMapper.registerModule(KotlinModule.Builder().build())
    }

    override fun run(config: Configuration, environment: Environment) {
        environment.jersey().register(KotlinCoroutineFeature::class.java)
    }
}
