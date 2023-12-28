# Dropwizard Kotlin [![Build Status](https://jenkins.dragon.zone/buildStatus/icon?job=dragonzone/dropwizard-kotlin/master)](https://jenkins.dragon.zone/blue/organizations/jenkins/dragonzone%2Fdropwizard-kotlin/activity?branch=master) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/zone.dragon.dropwizard/dropwizard-kotlin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/zone.dragon.dropwizard/dropwizard-kotlin/)

This bundle integrates various components of the kotlin ecosystem with Dropwizard to better facilitate the use of
Kotlin as the native language in which to build applications and components.

To use this bundle, add it to your application in the initialize method:

    @Override
    public void initialize(Bootstrap<T> bootstrap) {
        bootstrap.addBundle(new KotlinBundle<>());
    }

## Coroutines

Coroutines can easily be executed both at the application level and at the request level with structured concurrency
semantics.

### Application Scope

A `CoroutineScope` is made available at the container level for tracking background operations and ensuring a timely and
clean shutdown. This can be injected into any class and used to launch coroutines in place of `GlobalScope`:

```kotlin
class Test @Inject constructor(
    @Named(ApplicationCoroutineScope.NAME)
    private val appScope: CoroutineScope
) : ApplicationEventListener {
    override fun onEvent(event: ApplicationEvent) {
        if (event.type == INITIALIZATION_START) {
            appScope.launch {
                delay(10_000)
                println("It's been 10 seconds since the application initialized!")
            }
        }
    }

    override fun onRequest(requestEvent: RequestEvent): RequestEventListener? = null
}

environment.jersey().register(Test::class.java)
```

To access this scope, inject a parameter of type `CoroutineScope` and qualified by the name
`ApplicationCoroutineScope.NAME` into any component. This scope will be active until the application is either reloaded
or destroyed, and upon application shutdown will be cancelled to notify all active children of pending shutdown.

### Request Scope

Coroutines are natively supported as resource handlers. Jersey resource methods with the `suspend` modifier will
automatically be detected and executed in a suspending request context, freeing up request threads to handle other
requests when the handlers block:

```kotlin
class MyResource {

    @GET
    @Path("/wakeup")
    suspend fun lazyHelloWorld(): String {
        delay(10_000)
        return "Eh... is the sun up yet?"
    }
}
```

#### MDC

THe SLF4J MDC should be properly preserved from the request filters through the coroutine and the response filters.

#### Dispatching

Resource coroutines are dispatched using the Jetty thread pool, providing simplified thread management for the application. This behavior
can be overridden by binding a custom dispatcher to `CoroutineDispatcher` in Jersey/HK2, though keep in mind that it will need to be safe
for blocking since Jersey writes responses in a blocking manner.

## Jackson Serialization

Support for serializing and deserializing kotlin types with the built-in Jackson `ObjectMapper` is enabled, allowing
the use of kotlin types in configuration and request/response payloads.
