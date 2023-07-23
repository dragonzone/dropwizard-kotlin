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

import jakarta.inject.Singleton
import jakarta.ws.rs.core.Configuration
import mu.KLogging
import org.glassfish.jersey.server.model.ModelProcessor
import org.glassfish.jersey.server.model.Resource
import org.glassfish.jersey.server.model.ResourceMethod
import org.glassfish.jersey.server.model.ResourceModel
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

/**
 * Model Processor to alter resource methods handled by suspending kotlin functions to have the correct response type
 * for matching message body writers
 *
 * @author Bryan Harclerode
 */
@Singleton
class CoroutineModelProcessor : ModelProcessor {

    companion object : KLogging()

    private fun processModel(originalModel: ResourceModel, subresource: Boolean): ResourceModel {
        val modelBuilder = ResourceModel.Builder(subresource)
        for (originalResource in originalModel.resources) {
            modelBuilder.addResource(updateResource(originalResource))
        }
        return modelBuilder.build()
    }

    override fun processResourceModel(resourceModel: ResourceModel, configuration: Configuration): ResourceModel {
        return processModel(resourceModel, false)
    }

    override fun processSubResource(subResourceModel: ResourceModel, configuration: Configuration): ResourceModel {
        return processModel(subResourceModel, true)
    }

    /**
     * Checks if a given resource is handled by a suspendable kotlin function, and if so, returns the actual return type
     * for that method
     */
    private fun isCoroutine(method: ResourceMethod): Type? {
        val function = method.invocable?.handlingMethod?.kotlinFunction
        return if (function?.isSuspend == true) {
            function.returnType.javaType
        } else {
            null
        }
    }

    private fun updateResource(original: Resource): Resource {
        // replace all methods on this resource, and then recursively repeat upon all child resources
        val resourceBuilder = Resource.builder(original)
        for (childResource in original.childResources) {
            resourceBuilder.replaceChildResource(childResource, updateResource(childResource))
        }
        for (originalMethod in original.resourceMethods) {
            val asyncResponseType = isCoroutine(originalMethod)
            if (asyncResponseType != null) {
                logger.debug { "Updating return type of $originalMethod to suspended type $asyncResponseType" }
                resourceBuilder.updateMethod(originalMethod).routingResponseType(asyncResponseType)
            }
        }
        return resourceBuilder.build()
    }
}
