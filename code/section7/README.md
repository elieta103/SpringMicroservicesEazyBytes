## Seccion 07 Configurando MySQL

- Se eliminan las dependencias de rabbit, para evitar que el sistema se ralentize
  - configserver eliminar
    - spring-cloud-config-server
    - spring-cloud-starter-bus-amqp
    - Datos de la conexion a rabbitmq(rabbitmq, host, port)
  - accounts, cards, loans eliminar
    - spring-cloud-starter-bus-amqp
    - Datos de la conexion a rabbitmq(rabbitmq, host, port)

- Eliminar dependencias de H2 (accounts, cards, loans)
  - h2
- Agregar dependencias de MySQL (accounts, cards, loans)
  - mysql-connector-j
- Modificar datos de la conexion(accounts, cards, loans)

### Generar imagenes con Docker
Soporte para diferentes profiles(default, qa, prod)

- ...accounts>mvn compile jib:dockerBuild
- ...cards>mvn compile jib:dockerBuild
- ...loans>mvn compile jib:dockerBuild
- ...configserver>mvn compile jib:dockerBuild

- ...section7>docker image push docker.io/gresshel/accounts:s7
- ...section7>docker image push docker.io/gresshel/cards:s7
- ...section7>docker image push docker.io/gresshel/loans:s7
- ...section7>docker image push docker.io/gresshel/configserver:s7

...section7\docker-compose\default>docker compose up -d

Si se requiere apuntar a un nuevo perfil, NO SE GENERAN NUEVOS CONTAINERS
Solo se vuelve a ejecutar :

- docker compose down
- ...section7\docker-compose\default>docker compose up -d
- ...section7\docker-compose\qa>docker compose up -d
- ...section7\docker-compose\prod>docker compose up -d





  

- docker run -p 3306:3306 --name accountsdb -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=accountsdb -d mysql
- docker run -p 3307:3306 --name cardsdb -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=cardsdb -d mysql
- docker run -p 3308:3306 --name loansdb -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=loansdb -d mysql



### Crear Spring Cloud Config Server
1. Dependencias Config Server
   - spring-boot-starter-actuator
   - spring-cloud-config-server
   - spring-boot-starter-test
2. Agregar en ConfigServerApplication -> @EnableConfigServer

### Configuracion Local 
1. Agregar archivos en ruta local resources\config. Se llaman igual que el MS
    - accounts.yml, accounts-prod.yml, accounts-qa.yml
    - cards.yml, cards-prod.yml, cards-qa.yml
    - loans.yml, loans-prod.yml, loans-qa.yml
2. Configuración Inicial
```
spring:
  application:
    name: "configserver"
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: "classpath:/config"
          #search-locations: "file:///C:/Workspace/Code/cursosUdemy/SpringMicroservicesEazyBytes/code/section6/v2-spring-cloud-config/configserver/src/main/resources/config"
          #search-locations: "file:///Users//eazybytes//Documents//config"
     
server:
  port: 8071
```
3. Validar Configuraciones del Config Server
  - http://localhost:8071/accounts/native
  - http://localhost:8071/accounts/qa
  - http://localhost:8071/accounts/prod

4. En los clientes(accounts, loans, cards) se debe agregar
  - Dependencias 
    - spring-cloud-starter-config
  - Configuraciones properties
```
spring:
  application:
    name: "accounts"
  profiles:
    active: "prod"
  config:
    import: "optional:configserver:http://localhost:8071/"
```
  - Configuraciones pom.xml
```
<spring-cloud.version>2023.0.0</spring-cloud.version>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-dependencies</artifactId>
				<version>${spring-cloud.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
```

### Configuracion Remota con GIT
1. Configuraciones properties
```
spring:
  application:
    name: "configserver"
  profiles:
    active: git
  cloud:
    config:
      server:
        git:
          uri: "https://github.com/elieta103/SpringMicroservicesEazyBytesConfigServer.git"
          default-label: main
          timeout: 5
          clone-on-start: true
          force-pull: true
```

2. Validar Configuraciones del Config Server
  - http://localhost:8071/accounts/native
  - http://localhost:8071/accounts/qa
  - http://localhost:8071/accounts/prod


### Encriptado / Desencriptado de propiedades en Config Server
1. El secret que Sping Cloud Config usa para encriptar/desencriptar properties
```
encrypt:
  key: "45D81EC1EF61DF9AD8D3E5BB397F9"
```
2. Se llama al endpoint : http://localhost:8071/encrypt, pasando el dato a encriptar
3. Se modifica el email en el accounts profile prod, con el dato obtenido en el paso anterior
  - email: "{cipher}87d07d760d909045b641b66afe1f61ef62f13e1842e6f1fdbc606806f79a8e55d4880356006a11995e5c2c171b32831f
4. Cuando se llama al endpoint : http://localhost:8071/accounts/prod, se desencripta y muestra el mail original
  - ... "accounts.contactDetails.email": "aishwarya@eazybank.com" 
5. Si el secret es incorrecto : (45D81EC1EF61DF9AD8D3E5BB397F9xyz)  o pasó algun error mostrará
  - ... "invalid.accounts.contactDetails.email": "<n/a>"


### Refresh properties en tiempo de ejecucion con Actuator
1. Agregar properties en todos los MS (configserver, accounts, loans, cards)
  - spring-boot-starter-actuator
2. Los DTOs son records y sus propiedades son final, no se pueden modificar, se cambian a classe normal
  - Cambiar AccountsContactInfoDto Record -> AccountsContactInfoDto class
3. Habilitar rutas de actuator (accounts, cards, loans):
```
management:
  endpoints:
    web:
      exposure:
        include: "*"
```
4. Cambiar alguna propiedad del repositorio remoto GIT(Commit Changes) 
   - Accounts service profile prod http://localhost:8080/api/contact-info
5. Llamar actuator de Accounts Service: http://localhost:8080/actuator/refresh
6. Observar cambios
   - Accounts service profile prod http://localhost:8080/api/contact-info


### Spring Cloud Bus
- Enlaza todos los nodos de un sistema distribuido con un broker de mensajes ligero (RabbitMQ).
- Se puede usar para propagar los cambios de estado, configuración o algun otra instruccion de administración.
1. Rabbit debe estar corriendo
  - docker run -it --rm --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.12-management
2. Dependencias en MS(configserver, accounts, cards, loans)
  - spring-cloud-starter-bus-amqp
  - spring-boot-starter-actuator
3. Habilitar rutas de actuator (accounts, cards, loans):
```
management:
  endpoints:
    web:
      exposure:
        include: "*"
```
4. Detalles de conexión para RabbitMQ (accounts, cards, loans):
```
spring
  rabbitmq:
    host: "localhost"
    port: 5672
    username: "guest"
    password: "guest"
```
5. Modificar algun dato de los properties profile prod de (accounts, cards, loans)
6. Llamar a Bus Config : http://localhost:8080/actuator/busrefresh, Devuelve un 204
7. A diferencia del actuator solo se llama una vez, y no una por cada MS(accounts, loans, cards)
   Realiza el cambio a todas las instancia registradas en RabbitMQ


### Spring Cloud Monitor Con WebHook Git
- Enfoque automatizado sin invocar ninguna API, para el refresh de las propiedades
- Para poder utilizar Cloud Monitor, debe estar activo Spring Cloud Bus, completo el punto anterior.
- Config Server, expone un endpoint Rest /monitor
- Permite enviar una notificación cuando ocurren algun evento en GIT, y notifica a los MS mediante
  un broker de mensajes ligeros que ocurrio un cambio y lo actualiza.

1. Dependencies 
  - spring-cloud-config-monitor (configserver)
  - spring-cloud-starter-bus-amqp (configserver, accounts, cards, loans)
  - spring-boot-starter-actuator (configserver, accounts, cards, loans)

2. Habilitar rutas de actuator, busrefresh (configserver):
```
management:
  endpoints:
    web:
      exposure:
        include: "*"
```
3. Configuracion RabbitMQ (configserver)
```
  rabbitmq:
    host: "localhost"
    port: 5672
    username: "guest"
    password: "guest"
```
4. Desde GitHub crear un WebHook
- WebHook Permite que notificaciones sean entregadas a un Server externo, cuando 
  ocurren determinados eventos en GitHub
- Desde el repositorio :  elieta103/SpringMicroservicesEazyBytesConfigServer
   - Settings -> WebHooks -> Add WebHooks
   - Se usa hookdeck.com, como mediador para el envío de la notificacion, ya que
     no es posible enviar notificaciones a : http://localhost:8071/monitor
     - Ir a : https://hookdeck.com/
     - Developers Menu -> Hookdeck Console -> Add Destination
     - En PowerShell seguir los pasos de la pantalla de abajo
     - En la pagina de GitHub para configurar el webhook
       - https://hkdk.events/v1ve9a4pemjw4b
       - application/json
       - Enable SSL Verification
       - Just push event
       - Active
       - Add WebHook
     - Reiniciar los servicios
     - Modificar alguna de las propiedades y observar como se modifican los cambios al hacer commit en el repo.

### Configuracion HookDeck
```
PS C:\Users\Asus> $PSVersionTable.PSVersion

Major  Minor  Build  Revision                                                                                                                                                                   -----  -----  -----  --------                                                                                                                                                                   5      1      19041  4291                                                                                                                                                                                                                                                                                                                                                                       
PS C:\Users\Asus> Set-ExecutionPolicy RemoteSigned -scope CurrentUser

Cambio de directiva de ejecución
La directiva de ejecución te ayuda a protegerte de scripts en los que no confías. Si cambias dicha directiva, podrías exponerte a los riesgos de seguridad descritos en el tema de la Ayuda
about_Execution_Policies en https:/go.microsoft.com/fwlink/?LinkID=135170. ¿Quieres cambiar la directiva de ejecución?
[S] Sí  [O] Sí a todo  [N] No  [T] No a todo  [U] Suspender  [?] Ayuda (el valor predeterminado es "N"): s
PS C:\Users\Asus> iwr -useb get.scoop.sh | iex
Initializing...
Downloading...
Creating shim...
Adding ~\scoop\shims to your path.
Scoop was installed successfully!
Type 'scoop help' for instructions.
PS C:\Users\Asus> scoop bucket add hookdeck https://github.com/hookdeck/scoop-hookdeck-cli.git
Checking repo... OK
The hookdeck bucket was added successfully.
PS C:\Users\Asus> scoop install hookdeck
Installing '7zip' (23.01) [64bit] from 'main' bucket
7z2301-x64.msi (1.8 MB) [=============================================================================================================================================================] 100%
Checking hash of 7z2301-x64.msi ... ok.
Extracting 7z2301-x64.msi ... done.
Linking ~\scoop\apps\7zip\current => ~\scoop\apps\7zip\23.01
Creating shim for '7z'.
Creating shim for '7zFM'.
Making C:\Users\Asus\scoop\shims\7zfm.exe a GUI binary.
Creating shim for '7zG'.
Making C:\Users\Asus\scoop\shims\7zg.exe a GUI binary.
Creating shortcut for 7-Zip (7zFM.exe)
Persisting Codecs
Persisting Formats
Running post_install script...
'7zip' (23.01) was installed successfully!
Notes
-----
Add 7-Zip as a context menu option by running: "C:\Users\Asus\scoop\apps\7zip\current\install-context.reg"
Installing 'hookdeck' (0.8.6) [64bit] from 'hookdeck' bucket
hookdeck_0.8.6_windows_amd64.tar.gz (3.6 MB) [========================================================================================================================================] 100%
Checking hash of hookdeck_0.8.6_windows_amd64.tar.gz ... ok.
Extracting hookdeck_0.8.6_windows_amd64.tar.gz ... done.
Linking ~\scoop\apps\hookdeck\current => ~\scoop\apps\hookdeck\0.8.6
Creating shim for 'hookdeck'.
'hookdeck' (0.8.6) was installed successfully!
PS C:\Users\Asus> hookdeck login --cli-key 2e7vzukp573x2gjc3pj4buuza4jpqbs37ribgbbcjqgeicicl7
> Done! The Hookdeck CLI is configured with your console Sandbox
PS C:\Users\Asus> hookdeck listen [port] Source
🚩 Not connected with any account. Creating a guest account...
? What path should the webhooks be forwarded to (ie: /webhooks)? interrupt
interrupt
PS C:\Users\Asus> hookdeck listen 8071 Source
? What path should the webhooks be forwarded to (ie: /webhooks)? /monitor
? What's your connection label (ie: My API)? localhost

Dashboard
👉 Inspect and replay webhooks: https://console.hookdeck.com?source_id=src_v1ve9a4pemjw4b

Source Source
🔌 Webhook URL: https://hkdk.events/v1ve9a4pemjw4b

Connections
localhost forwarding to /monitor

> Ready! (^C to quit)
```

###  Readiness-state & Liveness-state:
- Evita que se levanten servicios sin antes comprobar que sus dependencias se encuentren listas.
- Liveness probe sends a signal that the container or application is either alive o dead.
- In simple words liveness answer a true or false question: "Is this container alive?"
- Readiness probe used to know whether the container oa app being probed is ready to start receiving network traffic.
- In simple words readiness answer a true or false question : "Is this container ready to receive traffic network?".

- Para config server
  - Debe estar la dependencia del actuator : 
    - spring-boot-starter-actuator
  - http://localhost:8071/actuator/health
  - http://localhost:8071/actuator/health/liveness
  - http://localhost:8071/actuator/health/readiness
```
management:
  endpoints:
    web:
      exposure:
        include: "*"
  health:
    readiness-state:
      enabled: true
    liveness-state:
      enabled: true
  endpoint:
    health:
      probes:
        enabled: true
```
- 

### Docker Compose
- Soporte para diferentes profiles(default, qa, prod)
  - ...accounts>mvn compile jib:dockerBuild
  - ...cards>mvn compile jib:dockerBuild
  - ...loans>mvn compile jib:dockerBuild
  - ...configserver>mvn compile jib:dockerBuild
  
  - ...v2-spring-cloud-config>docker image push docker.io/gresshel/accounts:s6
  - ...v2-spring-cloud-config>docker image push docker.io/gresshel/cards:s6
  - ...v2-spring-cloud-config>docker image push docker.io/gresshel/loans:s6
  - ...v2-spring-cloud-config>docker image push docker.io/gresshel/configserver:s6
  
  - ...v2-spring-cloud-config\docker-compose\default>docker compose up -d

- Si se requiere apuntar a un nuevo perfil, NO SE GENERAN NUEVOS CONTAINERS
- Solo se vuelve a ejecutar :
  - docker compose down
  - ...v2-spring-cloud-config\docker-compose\default>docker compose up -d
  - ...v2-spring-cloud-config\docker-compose\qa>docker compose up -d
  - ...v2-spring-cloud-config\docker-compose\prod>docker compose up -d
  

### Haciendo el reemplazo de properties con Spring Cloud Monitor y https://hookdeck.com/
```
C:\Users\Asus>scoop bucket add hookdeck https://github.com/hookdeck/scoop-hookdeck-cli.git
WARN  The 'hookdeck' bucket already exists. To add this bucket again, first remove it by running 'scoop bucket rm hookdeck'.

C:\Users\Asus>scoop bucket rm hookdeck

C:\Users\Asus>scoop bucket add hookdeck https://github.com/hookdeck/scoop-hookdeck-cli.git
Checking repo... OK
The hookdeck bucket was added successfully.

C:\Users\Asus>scoop install hookdeck

C:\Users\Asus>hookdeck login --cli-key 2e7vzukp573x2gjc3pj4buuza4jpqbs37ribgbbcjqgeicicl7
* Verifying credentials... unexpected http status code: 401 Unauthorized

C:\Users\Asus>hookdeck logout
Logging out...
Credentials have been cleared for the default project.

C:\Users\Asus>hookdeck login --cli-key 2e7vzukp573x2gjc3pj4buuza4jpqbs37ribgbbcjqgeicicl7
> Done! The Hookdeck CLI is configured with your console Sandbox

C:\Users\Asus>hookdeck listen 8071 Source
🚩 Not connected with any account. Creating a guest account...
? What path should the webhooks be forwarded to (ie: /webhooks)? /monitor
? What's your connection label (ie: My API)? localhost

Dashboard
👤 Console URL: https://api.hookdeck.com/signin/guest?token=548ucj8jjr9w9cmk8jihh02w4ef4kmp7jmi4y9s3hrk64xvu3u
Sign up in the Console to make your webhook URL permanent.

Source Source
🔌 Webhook URL: https://hkdk.events/ua1sk3p6wqmwmx

Connections
localhost forwarding to /monitor

> Ready! (^C to quit)
> Reconnected!
> Reconnected!
2024-05-01 13:37:58 [200] POST http://localhost:8071/monitor | https://console.hookdeck.com/?event_id=evt_cVEKxvNdar5ZL1Cqe0
- https://hookdeck.com/ -> Console HookDeck
```

