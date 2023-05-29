package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.ws.rs.core.Context
import org.glassfish.hk2.utilities.reflection.ParameterizedTypeImpl
import org.glassfish.jersey.model.Parameter
import org.glassfish.jersey.model.internal.spi.ParameterServiceProvider
import org.glassfish.jersey.server.model.Parameter.ServerParameterService
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import org.glassfish.jersey.server.model.Parameter as ServerParameter

/**
 * A [ParameterServiceProvider] for jersey server's [Parameter][ServerParameter], which identifies [Continuation]
 * parameters and overrides the detected properties to make them compatible with Jersey
 */
class ContinuationAwareParameterServiceProvider : ServerParameterService(), ParameterServiceProvider {
    override fun getParameterCreationFactory(): Parameter.ParamCreationFactory<ServerParameter> {
        // Delegate behavior to the real implementation by default, and only intercept the hooks we need to catch
        // Continuation method parameters.
        val delegate = super.getParameterCreationFactory()
        return object : Parameter.ParamCreationFactory<ServerParameter> by delegate {
            override fun createParameter(
                markers: Array<out Annotation>?,
                marker: Annotation?,
                source: Parameter.Source?,
                sourceName: String?,
                rawType: Class<*>,
                type: Type,
                encoded: Boolean,
                defaultValue: String?
            ): ServerParameter {
                if (rawType == Continuation::class.java) {
                    // Jersey will complain that the parameter is not concrete because it has a wildcard type like
                    // Continuation<? super SomeReturnType>
                    // To fix this, we replace the wildcard with its concrete lower bound, such as
                    // Continuation<SomeReturnType>
                    val originalGenericType = type as ParameterizedType
                    val newGenericType = ParameterizedTypeImpl(
                        originalGenericType.rawType,
                        (originalGenericType.actualTypeArguments[0] as WildcardType).lowerBounds[0]
                    )
                    // Because the parameter has no annotations, Jersey will consider it the entity parameter by default
                    // and complain that the method has multiple entity parameters or complain that methods which should
                    // not have entity parameters (such as GET) have one.
                    // To fix this, we change the parameter source to Context and mock out the annotations to such an
                    // effect.
                    return delegate.createParameter(
                        arrayOf(Context()),
                        Context(),
                        Parameter.Source.CONTEXT,
                        Parameter.Source.CONTEXT.name,
                        rawType,
                        newGenericType,
                        encoded,
                        defaultValue
                    )

                }
                return delegate.createParameter(
                    markers, marker, source, sourceName, rawType, type, encoded, defaultValue
                )
            }
        }
    }
}
