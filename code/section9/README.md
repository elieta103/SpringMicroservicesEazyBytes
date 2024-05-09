## Spring Cloud Gateway, Seccion 09

### Configuracion
 - Dependencias Maven 
   - spring-boot-starter-actuator
   - spring-cloud-starter-config
   - spring-cloud-starter-gateway
   - spring-cloud-starter-netflix-eureka-client
 - Agregar plugin para generar imagen Jib Google
   - <groupId>com.google.cloud.tools</groupId>
   - <artifactId>jib-maven-plugin</artifactId>
 - Propiedades
   - Conectarse con Eureka y obtener la información sobre los microservicios
   - y con esa información redirija el trafico hacia el servicio individual
```
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lowerCaseServiceId: true
```

### Habilita / Deshabilita la busqueda por defecto de los servicios con Eureka
```
      discovery:
        locator:
          enabled: true
```

### ApiGateway mayusculas / minusculas
- Crear account, sin propiedad(lowerCaseServiceId: true) :  http://localhost:8072/ACCOUNTS/api/create
- Crear account, con propiedad(lowerCaseServiceId: true) :  http://localhost:8072/accounts/api/create

### ApiGateway Rutas personalizadas
- Dentro de : GatewayserverApplication
```
@Bean
	public RouteLocator eazyBankRouteConfig(RouteLocatorBuilder routeLocatorBuilder) {
		return routeLocatorBuilder.routes()
						.route(p -> p
								.path("/eazybank/accounts/**")
								.filters( f -> f.rewritePath("/eazybank/accounts/(?<segment>.*)","/${segment}")										
								.uri("lb://ACCOUNTS"))
						...
						...
	}	
```

### Agregando Filtros 
- .addResponseHeader("X-Response-Time", LocalDateTime.now().toString()))

### Agregando Filtros personalizados
- Se requiere añadir logeo y un ID de correlacion/rastero para el request
- RequestTraceFilter, genera un Id de rastero cada vez que llega un request
- ResponseTraceFilter, añade el Id de rastreo en la respuesta
- FilterUtility, logica común a ambos filters
- Las implementaciones de RequestTraceFilter y ResponseTraceFilter, son equivalentes:
```
@Order(1)
@Component
public class RequestTraceFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
       ...
       return chain.filter(exchange);
    }
}
```
- VS
```
@Configuration
public class ResponseTraceFilter {
    @Bean
    public GlobalFilter postGlobalFilter() {
        return (exchange, chain) -> {...}
    }
}
```
- Imprima el log, si es tipo DEBUG , y esta bajo com.eazybytes.gatewayserver
```
logging:
  level:
    com:
      eazybytes:
        gatewayserver: DEBUG
```
- En los servicios(accounts, cards, loans), se agrega el log del correlationId, como parametro :
- @RequestHeader("eazybank-correlation-id") String correlationId
- Para validar los cambios:
  - Crear nueva account, loans, card, por medio del ApiGateway
    - POST http://localhost:8072/eazybank/accounts/api/create
    - POST http://localhost:8072/eazybank/cards/api/create?mobileNumber=4354437687
    - POSt http://localhost:8072/eazybank/loans/api/create?mobileNumber=4354437687
  - Llamar a recuperar los datos por medio del ApiGateway
    - GET http://localhost:8072/eazybank/accounts/api/fetchCustomerDetails?mobileNumber=4354437687
  - Validar desde postman, que se genera un Header :
    - eazybank-correlation-id = f7dfd0e2-bdab-42e2-91b9-74ad7f12638d

### Docker Compose
Docker Compose
- Soporte para diferentes profiles(default, qa, prod)
  - ...accounts>mvn compile jib:dockerBuild
  - ...cards>mvn compile jib:dockerBuild
  - ...loans>mvn compile jib:dockerBuild
  - ...configserver>mvn compile jib:dockerBuild
  - ...eurekaserver>mvn compile jib:dockerBuild
  - ...gatewayserver>mvn compile jib:dockerBuild

  - ...section9>docker image push docker.io/gresshel/accounts:s9
  - ...section9>docker image push docker.io/gresshel/cards:s9
  - ...section9>docker image push docker.io/gresshel/loans:s9
  - ...section9>docker image push docker.io/gresshel/configserver:s9
  - ...section9>docker image push docker.io/gresshel/eurekaserver:s9
  - ...section9>docker image push docker.io/gresshel/gatewayserver:s9

  - ...section9\docker-compose\default>docker compose up -d


Si se requiere apuntar a un nuevo perfil, NO SE GENERAN NUEVOS CONTAINERS

Solo se vuelve a ejecutar :

- docker compose down
- ...section9\docker-compose\default>docker compose up -d
- ...section9\docker-compose\qa>docker compose up -d
- ...section9\docker-compose\prod>docker compose up -d
