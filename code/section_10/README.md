## Microservicios Resilientes, Resilience4J, Retry Sección 10

### Circuit Breaker Pattern

- Tres principales estados
    - CLOSED. Cuando la app se inicia, por default está en este estado,
      permite todo el trafico hacia un microservice.
    - OPEN. Si la mayoría de las peticiones están fallando, pasa a este estado,
      la trancisión entre CLOSED y OPEN, se basa en la tasa de fallos.
    - HALF_OPEN. Periodicamente, circuit breaker checa si el problema se ha resuelto,
      permitiendo algunas peticiones, basado en los resultados, decidira si pasa a OPEN ó CLOSED.

### Implementación

- Se implementa en :
    - ApiGateway
    - Accounts, ya que este llama a Loans y Cards.

### Configuracion ApiGateway

- ApiGateway
    - Configuraciones
        - Dependencias maven
            - spring-cloud-starter-circuitbreaker-reactor-resilience4j
        - En GatewayserverApplication.class, donde se aplica el routing.

          ```
              .route(p -> p.path("/eazybank/accounts/**")
                 .filters( f -> f.rewritePath("/eazybank/accounts/(?<segment>.*)","/${segment}")
                                 .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                 .circuitBreaker(config -> config.setName("accountsCircuitBreaker")))
                 .uri("lb://ACCOUNTS"))
          ```
        - En las properties
            ```
            resilience4j.circuitbreaker:
              configs:
                default:
                  slidingWindowSize: 10
                  permittedNumberOfCallsInHalfOpenState: 2
                  failureRateThreshold: 50
                  waitDurationInOpenState: 10000
           ```

- Levantar los servicios (configserver, eurekaserver, accounts, gatewayserver)
- Urls para probar el circuit breaker implementado en ApiGateway
    1. Visitar : http://localhost:8072/actuator
    2. Verificar : http://localhost:8072/actuator/circuitbreakers  (No mostrará info la 1era vez)
    3. Cargar info : http://localhost:8072/eazybank/accounts/api/contact-info
    4. ReVerificar : http://localhost:8072/actuator/circuitbreakers (Mostrará información)

- Para verificar los sucesivas llamadas del circuitBreaker
    - API Invocada :  http://localhost:8072/eazybank/accounts/api/contact-info
    - Event CircuitBreaker : http://localhost:8072/actuator/circuitbreakerevents?name=accountsCircuitBreaker

- Hasta este punto se puede poner un breakpoint(getContactInfo() de AccountController) y simular llamadas lentas
- Debería pasar del estado CLOSED al OPEN

### FallBack Configuracion ApiGateway
- Crear controller en ApiGateway
  - com.eazybytes.gatewayserver.controller.FallbackController
```
@RestController
public class FallbackController {
    @RequestMapping("/contactSupport")
    public Mono<String> contactSupport() {
        return Mono.just("An error occurred. Please try after some time or contact support team!!!");
    }
}
```
  - Agregar en los routes del ApiGateway
```
						.route(p -> p
								.path("/eazybank/accounts/**")
								.filters( f -> f.rewritePath("/eazybank/accounts/(?<segment>.*)","/${segment}")
										.addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
										.circuitBreaker(config -> config.setName("accountsCircuitBreaker")
												.setFallbackUri("forward:/contactSupport")))
								.uri("lb://ACCOUNTS"))
```
- Hasta este punto se puede poner un breakpoint(getContactInfo() de AccountController) y simular llamadas lentas
- Debería mostrar un mensaje : 
  - "An error occurred. Please try after some time or contact support team!!!"


### Circuit Breaker Pattern with Feign Client (accounts)
- Dependencias
  -  spring-cloud-starter-circuitbreaker-resilience4j
- Propiedades en application.yml, esto activa el circuit breaker para todos los clientes Feign
```
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true

resilience4j.circuitbreaker:
  configs:
    default:
      slidingWindowSize: 10
      permittedNumberOfCallsInHalfOpenState: 2
      failureRateThreshold: 50
      waitDurationInOpenState: 10000
````
- Agregar FallBack en los clients Feign
  - @FeignClient(name="cards", fallback = CardsFallback.class)
  - @FeignClient(name="loans", fallback = LoansFallback.class)
- Agregar class 
  - CardsFallback.java
  - LoansFallback.java
- En CustomerServiceImpl, fetchCustomerDetails()
  - validar null :  if(null != loansDtoResponseEntity) {...}   if(null != cardsDtoResponseEntity) {...}

- Circuit Breakers
  - http://localhost:8080/actuator/circuitbreakers
  - http://localhost:8080/actuator/circuitbreakerevents
  - Detener el servicio loans
    - La respuesta es :   "loansDto": null, y despues de varios intentos el estado es OPEN.


### Http Timeouts Configuración
- Se agregan los timeouts en el ApiGateway
-  connect-timeout  : Es el tiempo en el que el ApiGateway obtiene un hilo de conexión al ms accounts, cards, loans.
-  response-timeout : Es el tiempo máximo que el ApiGateway va esperar para recibir la respuesta de ms accounts, cards, loans.
- Son congiguraciones globales para todos los routes.
- Para probar, se puede agregar en el metodo : getContactInfo() de loans, un breakpoint.

```
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 1000
        response-timeout: 2s
```

### Retry Pattern en ApiGateway para Routes del servicio loans
- Patron de reintentos, cuando un servicio falla temporalmente.
- Retry Logic : Como realizar la invocación condicional, en función de codigos de error, excepciones o estados de respuesta.
- Backoff Strategy : Aumentar gradualmente el retardo entre cada intento.
- Circuit Breaker Integration : Permitir que despues de varios intentos fallidos consecutivos, el circuito se ponga 
  en OPEN.
- Idempotent Operations : Son aquellas operaciones que no provocan un efecto secundario, independiente de cuantas veces se llamen.
  - Implementar el patron de reintentos, solo en operaciones idempotentes.
  - Fetch / Get son idempotentes.
  - Post / Put no lo son.

- En accounts esta implementado del Circuit Breaker
- En loans se implementará el Retry.
```
                        .route(p -> p
							.path("/eazybank/loans/**")
							.filters( f -> f.rewritePath("/eazybank/loans/(?<segment>.*)","/${segment}")
									.addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
									.retry(retryConfig -> retryConfig.setRetries(3)
											.setMethods(HttpMethod.GET)
											.setBackoff(Duration.ofMillis(100),Duration.ofMillis(1000),2,true)))
							.uri("lb://LOANS"))
```
- Poner un break point, en getContactInfo() de loans  y llamar : http://localhost:8072/eazybank/loans/api/contact-info
```
2024-05-15T17:39:38.088-05:00 DEBUG 10512 --- [loans] [nio-8090-exec-2] c.e.loans.controller.LoansController     : Invoked Loans contact-info API
2024-05-15T17:39:44.059-05:00 DEBUG 10512 --- [loans] [nio-8090-exec-1] c.e.loans.controller.LoansController     : Invoked Loans contact-info API
2024-05-15T17:39:45.998-05:00 DEBUG 10512 --- [loans] [nio-8090-exec-3] c.e.loans.controller.LoansController     : Invoked Loans contact-info API
2024-05-15T17:39:47.317-05:00 DEBUG 10512 --- [loans] [nio-8090-exec-4] c.e.loans.controller.LoansController     : Invoked Loans contact-info API
```

### Retry Pattern en accounts
- Se implementa en el AccountsController -> getBuildInfo()

```
    @Retry(name = "getBuildInfo",fallbackMethod = "getBuildInfoFallback")
    @GetMapping("/build-info")
    public ResponseEntity<String> getBuildInfo() {...}
    
    // Debe mantener la misma firma, SIEMPRE SE AGREGA EL PARAMETRO : Throwable throwable
    // Por lo que si el metodo original tenia 1 parametro, el fallback tendra 2, con el Throwable throwable agregado.
    public ResponseEntity<String> getBuildInfoFallback(Throwable throwable) {...}
```
```
resilience4j.retry:
  configs:
    default:
      maxAttempts: 4
      waitDuration: 100
      enableExponentialBackoff: true
      exponentialBackoffMultiplier: 2

```
- En getBuildInfo(), se lanza una : throw new NullPointerException(); para las pruebas
- http://localhost:8072/eazybank/accounts/api/build-info
- servicios iniciados (configserver, eurekaserver, gatewayserver, accounts)
- La respuesta inmediata es : 0.9 que es el FallBackMethod()

```
2024-05-20T13:27:27.086-05:00 DEBUG 15644 --- [accounts] [nio-8080-exec-3] c.e.a.controller.AccountsController      : getBuildInfo() method Invoked
2024-05-20T13:27:27.202-05:00 DEBUG 15644 --- [accounts] [nio-8080-exec-3] c.e.a.controller.AccountsController      : getBuildInfo() method Invoked
2024-05-20T13:27:27.404-05:00 DEBUG 15644 --- [accounts] [nio-8080-exec-3] c.e.a.controller.AccountsController      : getBuildInfo() method Invoked
2024-05-20T13:27:27.811-05:00 DEBUG 15644 --- [accounts] [nio-8080-exec-3] c.e.a.controller.AccountsController      : getBuildInfo() method Invoked
2024-05-20T13:27:27.813-05:00 DEBUG 15644 --- [accounts] [nio-8080-exec-3] c.e.a.controller.AccountsController      : getBuildInfoFallback() method Invoked
```
- Si modificamos el valor: 
  - waitDuration: 100  a waitDuration: 500
- Siempre obtendremos :
  - An error occurred. Please try after some time or contact support team!!!
- TimeLimiter, Tiempo maximo de espera para completar una operación especifica...
- Se agrega configuracion del ApiGateway
```
	@Bean
	public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
		return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
				.circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
				.timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(4)).build()).build());
	}
```
- La respuesta deberia de ser :  
  - GET http://localhost:8072/eazybank/accounts/api/build-info   :   0.9

- Si se quiere agregar, que NO REINTENTE, si hay un NullPointerException() 
- o reintentar para otras excepciones, se configura :
```
resilience4j.retry:
  ...
      ignore-exceptions:
        - java.lang.NullPointerException
      retry-exceptions:
        - java.util.concurrent.TimeoutException
```

### Example Retry
- GET http://localhost:8888/retryable-operation?param1=param1&param2=param2
- La propiedad de la annotation, es la que controla los reintentos
```
@Retryable(retryFor = {RemoteServiceNotAvailableException.class},
            maxAttempts = 10,
            backoff = @Backoff(delay = 1000))
```

- Ejemplo 1 
```
int random = new Random().nextInt(1);
if (random % 2 == 0) {

2024-05-17T14:32:25.687-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] o.s.b.w.s.f.OrderedRequestContextFilter  : Bound request context to thread: org.apache.catalina.connector.RequestFacade@55f9d9e2
2024-05-17T14:32:25.687-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] o.s.web.servlet.DispatcherServlet        : GET "/retryable-operation?param1=param1&param2=param2", parameters={masked}, headers={masked} in DispatcherServlet 'dispatcherServlet'
2024-05-17T14:32:25.687-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] o.s.b.f.s.DefaultListableBeanFactory     : Returning cached instance of singleton bean 'resourceController'
2024-05-17T14:32:25.687-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to com.elhg.retryexample.ResourceController#validateSpringRetryCapability(String, String)
2024-05-17T14:32:25.688-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] o.s.web.method.HandlerMethod             : Arguments: [param1, param2]
2024-05-17T14:32:25.688-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : RetryContext retrieved: [RetryContext: count=0, lastException=null, exhausted=false]
2024-05-17T14:32:25.688-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Retry: count=0
2024-05-17T14:32:25.688-05:00  INFO 12952 --- [retry-example] [nio-8888-exec-4] c.elhg.retryexample.BackendAdapterImpl   : Number generate: 0
2024-05-17T14:32:26.696-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Checking for rethrow: count=1
2024-05-17T14:32:26.697-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Retry: count=1
2024-05-17T14:32:26.697-05:00  INFO 12952 --- [retry-example] [nio-8888-exec-4] c.elhg.retryexample.BackendAdapterImpl   : Number generate: 0
2024-05-17T14:32:27.699-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Checking for rethrow: count=2
2024-05-17T14:32:27.699-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Retry: count=2
2024-05-17T14:32:27.699-05:00  INFO 12952 --- [retry-example] [nio-8888-exec-4] c.elhg.retryexample.BackendAdapterImpl   : Number generate: 0
2024-05-17T14:32:28.702-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Checking for rethrow: count=3
2024-05-17T14:32:28.702-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Retry: count=3
2024-05-17T14:32:28.702-05:00  INFO 12952 --- [retry-example] [nio-8888-exec-4] c.elhg.retryexample.BackendAdapterImpl   : Number generate: 0
2024-05-17T14:32:29.715-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Checking for rethrow: count=4
2024-05-17T14:32:29.715-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Retry: count=4
2024-05-17T14:32:29.715-05:00  INFO 12952 --- [retry-example] [nio-8888-exec-4] c.elhg.retryexample.BackendAdapterImpl   : Number generate: 0
2024-05-17T14:32:29.715-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Checking for rethrow: count=5
2024-05-17T14:32:29.715-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.retry.support.RetryTemplate          : Retry failed last attempt: count=5
2024-05-17T14:32:29.717-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.w.s.m.m.a.HttpEntityMethodProcessor  : Using 'text/html', given [text/html, application/xhtml+xml, image/avif, image/webp, image/apng, application/xml;q=0.9, */*;q=0.8, application/signed-exchange;v=b3;q=0.7] and supported [text/plain, */*, application/json, application/*+json]
2024-05-17T14:32:29.717-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] o.s.w.s.m.m.a.HttpEntityMethodProcessor  : Writing ["Hello from fallback method!!!"]
2024-05-17T14:32:29.718-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] s.w.s.m.m.a.RequestMappingHandlerAdapter : Applying default cacheSeconds=-1
2024-05-17T14:32:29.719-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] o.s.web.servlet.DispatcherServlet        : No view rendering, null ModelAndView returned.
2024-05-17T14:32:29.719-05:00 DEBUG 12952 --- [retry-example] [nio-8888-exec-4] o.s.web.servlet.DispatcherServlet        : Completed 200 OK, headers={masked}
2024-05-17T14:32:29.719-05:00 TRACE 12952 --- [retry-example] [nio-8888-exec-4] o.s.b.w.s.f.OrderedRequestContextFilter  : Cleared thread-bound request context: org.apache.catalina.connector.RequestFacade@55f9d9e2
```
- Ejemplo 2
```
int random = new Random().nextInt(2)
if (random % 2 == 0) {

2024-05-17T14:37:23.134-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] o.s.b.w.s.f.OrderedRequestContextFilter  : Bound request context to thread: org.apache.catalina.connector.RequestFacade@50c74149
2024-05-17T14:37:23.142-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] o.s.web.servlet.DispatcherServlet        : GET "/retryable-operation?param1=param1&param2=param2", parameters={masked}, headers={masked} in DispatcherServlet 'dispatcherServlet'
2024-05-17T14:37:23.161-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] o.s.b.f.s.DefaultListableBeanFactory     : Returning cached instance of singleton bean 'resourceController'
2024-05-17T14:37:23.161-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to com.elhg.retryexample.ResourceController#validateSpringRetryCapability(String, String)
2024-05-17T14:37:23.195-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] o.s.web.method.HandlerMethod             : Arguments: [param1, param2]
2024-05-17T14:37:23.217-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] o.s.retry.support.RetryTemplate          : RetryContext retrieved: [RetryContext: count=0, lastException=null, exhausted=false]
2024-05-17T14:37:23.217-05:00 DEBUG 1908 --- [retry-example] [nio-8888-exec-1] o.s.retry.support.RetryTemplate          : Retry: count=0
2024-05-17T14:37:23.219-05:00  INFO 1908 --- [retry-example] [nio-8888-exec-1] c.elhg.retryexample.BackendAdapterImpl   : Number generate: 0
2024-05-17T14:37:24.233-05:00 DEBUG 1908 --- [retry-example] [nio-8888-exec-1] o.s.retry.support.RetryTemplate          : Checking for rethrow: count=1
2024-05-17T14:37:24.233-05:00 DEBUG 1908 --- [retry-example] [nio-8888-exec-1] o.s.retry.support.RetryTemplate          : Retry: count=1
2024-05-17T14:37:24.233-05:00  INFO 1908 --- [retry-example] [nio-8888-exec-1] c.elhg.retryexample.BackendAdapterImpl   : Number generate: 1
2024-05-17T14:37:24.261-05:00 DEBUG 1908 --- [retry-example] [nio-8888-exec-1] o.s.w.s.m.m.a.HttpEntityMethodProcessor  : Using 'text/html', given [text/html, application/xhtml+xml, image/avif, image/webp, image/apng, application/xml;q=0.9, */*;q=0.8, application/signed-exchange;v=b3;q=0.7] and supported [text/plain, */*, application/json, application/*+json]
2024-05-17T14:37:24.262-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] o.s.w.s.m.m.a.HttpEntityMethodProcessor  : Writing ["Hello from remote backend!!!"]
2024-05-17T14:37:24.278-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] s.w.s.m.m.a.RequestMappingHandlerAdapter : Applying default cacheSeconds=-1
2024-05-17T14:37:24.278-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] o.s.web.servlet.DispatcherServlet        : No view rendering, null ModelAndView returned.
2024-05-17T14:37:24.281-05:00 DEBUG 1908 --- [retry-example] [nio-8888-exec-1] o.s.web.servlet.DispatcherServlet        : Completed 200 OK, headers={masked}
2024-05-17T14:37:24.282-05:00 TRACE 1908 --- [retry-example] [nio-8888-exec-1] o.s.b.w.s.f.OrderedRequestContextFilter  : Cleared thread-bound request context: org.apache.catalina.connector.RequestFacade@50c74149
```

### Rate Limiter Pattern
- El patrón de diseño, Rate Limiter ayuda a controlar y limitar la taza de peticiones entrantes a un servicio o API.
- Provee un mecanismo para reforzar y limitar las peticiones entrantes.
- Previene (DoS) Denial of Service
- Devuelve un HTTP 429 Too Many Requests

### Rate Limiter Pattern ApiGateway
- Basado en Redis(Almacenamiento basado en cache)
- defaultReplenishRate : No. de peticiones permitidas por segundo. (Velocidad en la que se llena el 'token bucket' )
- Por Ej. Si se definen 100, significa que cada seg. se añadiran 100 tokens al bucket, asi que si esperas 2 seg.
-         y no se consume nada el buket tendrá 200 request.
- defaultBurstCapacity : No. peticiones que se van añadiendo por segundo, Es el numero de tokens que el 'token bucket',
-                        puede mantener.
- Por Ej. Si defaultBurstCapacity = 100, despues de 10 seg. si el usuario no hace nada
-        en esos 10 seg, el bucket va tener 1000 request y el numero pudiera crecer indefinidamente, para evitar esto, se
-        utiliza
- defaultRequestedTokens : Define cuantos tokens cuesta una peticion. Por default cada solicictud, costará un solo token
- Por Ej. defaultBurstCapacity = 200 y  defaultReplenishRate = 100, significa que en el primer seg.  aunque el usuario no pueda
-         utilizar sus fichas en el segundo seg. deberia poder utilizar todas sus 200 fichas a la vez

- Ej. Solo permitir una solicitud por minuto :
- replenishRate = 1, requestedTokens=60, burstCapacity=60

- Dependencias
  - spring-boot-starter-data-redis-reactive

```
	@Bean
	public RedisRateLimiter redisRateLimiter() {
	    // tokenPorSegundo = 1, capacidadRafaga =1, ostoSolicitud = 1
		return new RedisRateLimiter(1, 1, 1);
	}

	@Bean
	KeyResolver userKeyResolver() {
		return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("user"))
				.defaultIfEmpty("anonymous");
	}
```
- Se implementa el rateLimiter para tarjetas
```
.route(p -> p
							.path("/eazybank/cards/**")
							.filters( f -> f.rewritePath("/eazybank/cards/(?<segment>.*)","/${segment}")
									.addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
									.requestRateLimiter(config -> config.setRateLimiter(redisRateLimiter()).setKeyResolver(userKeyResolver())))
							.uri("lb://CARDS"))
```
- Se inicia el contenedor para Redis
- docker run -p 6379:6379 --name eazyredis -d redis
- Configuración de Redis
```
spring:
  data:
    redis:
      connect-timeout: 2s
      host: localhost
      port: 6379
      timeout: 1s
```
- Iniciar los servicios configserver, eurekaserver, gatewayserver, cards.
- Iniciar Redis
- Descargar Apache Benchmark
- Move into the Apache24/bin directory and copy both the ab and abs executable files to your project directory (optional).
- ab.exe -c 2 -n 10 -v 3 http://localhost:8072/eazybank/cards/api/contact-info
- -c Concurrency
-  This indicates the number of multiple requests to make at a time. For this test, we are sending 10 requests to our server concurrently at the same time.
- -v Verbose 
- Detailed Resume
- -n Request
- This indicates the number of requests to perform.

- Solo procesa 1, las 9 restantes aparecen como : 429 Too Many Requests
```
Server Software:
Server Hostname:        localhost
Server Port:            8072

Document Path:          /eazybank/cards/api/contact-info
Document Length:        213 bytes

Concurrency Level:      2
Time taken for tests:   0.092 seconds
Complete requests:      10
Failed requests:        9
   (Connect: 0, Receive: 0, Length: 9, Exceptions: 0)
Non-2xx responses:      9
Total transferred:      2146 bytes
HTML transferred:       213 bytes
Requests per second:    109.23 [#/sec] (mean)
Time per request:       18.310 [ms] (mean)
Time per request:       9.155 [ms] (mean, across all concurrent requests)
Transfer rate:          22.89 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    1   0.3      1       1
Processing:    12   14   2.7     13      21
Waiting:        9   12   2.9     11      18
Total:         12   15   2.7     14      22
```

### Rate Limiter Pattern Accounts
- Se aplica a : GET http://localhost:8072/eazybank/accounts/api/java-version
- Al llamar desde postman, se muestra :
- getJavaVersionFallback  Java 17

```
@RateLimiter(name= "getJavaVersion", fallbackMethod = "getJavaVersionFallback")
    @GetMapping("/java-version")
    public ResponseEntity<String> getJavaVersion() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(environment.getProperty("JAVA_HOME"));
    }

    public ResponseEntity<String> getJavaVersionFallback(Throwable throwable) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Java 17");
    }
```
- Propiedades
```
resilience4j.ratelimiter:
  configs:
    default:
      timeoutDuration: 1000
      limitRefreshPeriod: 5000
      limitForPeriod: 1
```



### BulkHead Pattern
- BulkHead(Mamparas en un barco, se puede inundar un compartimiento, pero no afecta al resto).
- Mejora la resistencia y aislamiento de los componentes dentro del sistema.
- Se puede aislar y limitar el impacto de fallos o cargas elevadas en un componente, para que no afecte otro.

### Order Aspect
- Si se tienen implementados varios patrones, el orden por default a seguir es:
- Retry(CircuitBreaker(RateLimiter(TimeLimiter(BulkHead(Function)))))
- 5     4              3            2           1  



### Docker Compose

Docker Compose

- Soporte para diferentes profiles(default, qa, prod)
    - ...accounts>mvn compile jib:dockerBuild
    - ...cards>mvn compile jib:dockerBuild
    - ...loans>mvn compile jib:dockerBuild
    - ...configserver>mvn compile jib:dockerBuild
    - ...eurekaserver>mvn compile jib:dockerBuild
    - ...gatewayserver>mvn compile jib:dockerBuild

    - ...section10>docker image push docker.io/gresshel/accounts:s10
    - ...section10>docker image push docker.io/gresshel/cards:s10
    - ...section10>docker image push docker.io/gresshel/loans:s10
    - ...section10>docker image push docker.io/gresshel/configserver:s10
    - ...section10>docker image push docker.io/gresshel/eurekaserver:s910    
    - ...section10>docker image push docker.io/gresshel/gatewayserver:s10

    - ...section10\docker-compose\default>docker compose up -d

Si se requiere apuntar a un nuevo perfil, NO SE GENERAN NUEVOS CONTAINERS

Solo se vuelve a ejecutar :

- docker compose down
- ...section10\docker-compose\default>docker compose up -d
- ...section10\docker-compose\qa>docker compose up -d
- ...section10\docker-compose\prod>docker compose up -d

### Test con los contenedores
- RateLimiter desde accounts
  - GET http://localhost:8072/eazybank/accounts/api/java-version
    - Una operación :  /opt/java/openjdk
    - Varias operaciones continuas : getJavaVersionFallback  Java 17
- RateLimiter desde apigateway
  - ...section_10>ab.exe -c 2 -n 10 -v 3 http://localhost:8072/eazybank/cards/api/contact-info
```
Concurrency Level:      2
Time taken for tests:   0.224 seconds
Complete requests:      10
Failed requests:        8
```
  - La configuración que tienen los contenedores es :
```
@Bean
	public RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(2, 2, 1);
	}
```