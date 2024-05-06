## Spring Cloud support for Client-Side service discovery, Seccion 08
- Provee :
  - Servicio                    Versiones anteriores              Versiones nuevas
  - Service discovery           - Spring Cloud Netflix's Eureka   - Spring Cloud Netflix's Eureka
  - Load balancer               - Ribbon                          - Spring Cloud LoadBalancer
  - Fault tolerance             - Hystrix                         - Resilience4J

- Se utilizó como base la sección 6, con la Base de Datos H2
- Para no ralentizar los servicios, se eliminó Spring Cloud Bus, rabbitmq
  - spring-cloud-starter-bus-amqp
  - spring-cloud-config-monitor
  - Propiedades relacionadas con rabbitmq
  
### Eureka Server
  - Dependencias Maven
    - spring-boot-starter-actuator
    - spring-cloud-starter-config
    - spring-cloud-starter-netflix-eureka-server

### Configuracion
  - @EnableEurekaServer en EurekaServerApplication
  - eureka.yml, no hay qa, prod. Configuraciones son iguales, no hay variaciones.
```
server:
  port: 8070

eureka:
  instance:
    hostname: localhost
  client:
    fetchRegistry: false
    registerWithEureka: false
    serviceUrl:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
```
### Validación
- Iniciar configserver,  http://localhost:8071/eurekaserver/default
- Iniciar eurekaserver,  http://localhost:8070/

### Eureka Client
- En cada servicio(accounts, cards, loans)
  - Dependencias Maven
    - spring-cloud-starter-netflix-eureka-client
  - Propiedades para conectar el cliente al registro de nombres
```
eureka:
  instance:
    preferIpAddress: true
  client:
    fetchRegistry: true
    registerWithEureka: true
    serviceUrl:
      defaultZone: http://localhost:8070/eureka/
      
management:
  info:
    env:
      enabled: true
  endpoint:
    shutdown:
      enabled: true
      
endpoints:
  shutdown:
    enabled: true
```
  - Propiedades de todos los servicio
    - http://localhost:8070/eureka/apps
  - Propiedades del actuator
    - GET http://192.168.0.11:8080/actuator/info
    - POST http://192.168.0.11:8080/actuator/shutdown
    - POST http://192.168.0.11:9000/actuator/shutdown

### Clientes OpenFeign
- Accounts cargará información de Cars y Loans
- Dependencias Maven
  - spring-cloud-starter-openfeign
- Configuración en AccountsApplication
  - @EnableFeignClients
- Cliente para cards
```
@FeignClient("cards")
public interface CardsFeignClient {

    @GetMapping(value = "/api/fetch",consumes = "application/json")
    public ResponseEntity<CardsDto> fetchCardDetails(@RequestParam String mobileNumber);

}
```

### EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP WHEN THEY'RE NOT. 
### RENEWALS ARE LESSER THAN THRESHOLD AND HENCE THE INSTANCES ARE NOT BEING EXPIRED JUST TO BE SAFE.
- Eureka Server entra en un estado de "auto-preservación", cuando algunas instancias de las que tiene
- registradas no envían heartbeat o ya no están disponibles.
- En resumen Eureka Server no entrará en pánico cuando no reciba los heartbeat de la mayoria de las
- instancias, en lugar de eso se calmará y entrará en modo de autoconservación.
- Es una salvación cuando haya fallos de red y trata de manejar los falsos positivos.


### Docker Compose
Docker Compose
- Soporte para diferentes profiles(default, qa, prod)
  - ...accounts>mvn compile jib:dockerBuild
  - ...cards>mvn compile jib:dockerBuild
  - ...loans>mvn compile jib:dockerBuild
  - ...configserver>mvn compile jib:dockerBuild
  - ...eurekaserver>mvn compile jib:dockerBuild

  - ...section8>docker image push docker.io/gresshel/accounts:s8
  - ...section8>docker image push docker.io/gresshel/cards:s8
  - ...section8>docker image push docker.io/gresshel/loans:s8
  - ...section8>docker image push docker.io/gresshel/configserver:s8
  - ...section8>docker image push docker.io/gresshel/eurekaserver:s8

  - ...section8\docker-compose\default>docker compose up -d


Si se requiere apuntar a un nuevo perfil, NO SE GENERAN NUEVOS CONTAINERS

Solo se vuelve a ejecutar :

- docker compose down
- ...section8\docker-compose\default>docker compose up -d
- ...section8\docker-compose\qa>docker compose up -d
- ...section8\docker-compose\prod>docker compose up -d

### Balanceo de cargas.
- Crear un account, card, loans
- El prestamo(loan), se creo con la instancia 8090
- La instancia 8091, no tiene esos datos en su base H2 local, por lo tanto al hacer
- llamadas sucesivas de : http://localhost:8080/api/fetchCustomerDetails?mobileNumber=4354437687
- eventualmente por el balanceo de cargas, llamara a la instancia del 8091 y marcara 
```
{   "apiPath": "uri=/api/fetchCustomerDetails",
    "errorCode": "INTERNAL_SERVER_ERROR",
    "errorMessage": "[404] during [GET] to [http://loans/api/fetch?mobileNumber=4354437687] [LoansFeignClient#fetchLoanDetails(String)]: [{\"apiPath\":\"uri=/api/fetch\",\"errorCode\":\"NOT_FOUND\",\"errorMessage\":\"Loan not found with the given input data mobileNumber : '4354437687'\",\"errorTime\":\"2024-05-06T01:56:48.116088791\"}]",
    "errorTime": "2024-05-06T01:56:48.119218299"
}
```
- lo cual es correcto.