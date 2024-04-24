## Seccion 04 Docker containers

### Enfoques para generar un contenedor
1. Dockerfile -> accounts
  - Crear un Dockerfile con las instrucciones
2. Buildpacks -> loans
  - Simplifica la contenerizacion, no requiere Dockerfile
3. Google Jib -> cards
  - Es una herramienta de codigo abierto, se carga como un plugin de maven

### Accounts Dockerfile
- Agregar en pom.xml : <packaging>jar</packaging>
- ...accounts>mvn clean install
- ...accounts>mvn spring-boot:run
- ...accounts>java -jar .\target\accounts-0.0.1-SNAPSHOT.jar
- ...accounts>docker built -t gresshel/accounts:s4 .
- ...accounts>docker run -d -p 8080:8080 gresshel/accounts:s4
- ...accounts>docker exec -ti ID_CONTAINER bash

### Loans Buildpacks
- http://buildpacks.io
- Atras de escena utiliza Paketo buildpacks
- Agregar en pom.xml : <packaging>jar</packaging>
- Agregar en pom.xml : 
```
<configuration>
    <image>
		<name>gresshel/${project.artifactId}:s4</name>
	</image>
</configuration>
```
- ...loans>mvn spring-boot:build-image
- ...loans>docker run -d -p 8090:8090 gresshel/loans:s4

### Cards Google Jib
- https://github.com/GoogleContainerTools/jib
- Solo funciona con Java
- Agregar en pom.xml : <packaging>jar</packaging>
- Agregar plugin en pom.xml
```
<plugin>
				<groupId>com.google.cloud.tools</groupId>
				<artifactId>jib-maven-plugin</artifactId>
				<version>3.4.1</version>
				<configuration>
					<to>
						<image>gresshel/${project.artifactId}:s4</image>
					</to>
				</configuration>
			</plugin>
```
- ...cards>mvn compile jib::dockerBuild
- ...cards>docker run -d -p 9000:9000 gresshel/cards:s4

### Pushing images en Docker Hub
- [gresshel@gmail.com, elieta103]
- docker image push docker.io/gresshel/accounts:s4

### Docker Compose
- ...accounts>docker compose up -d     RECREA E INICIA CONTAINERS
- ...accounts>docker compose down      DETIENE Y ELIMINA
- ...accounts>docker compose start     BUSCA CONTAINERS EXISTENTES E INTENTA LANZARLOS DE NUEVO
- ...accounts>docker compose stop      DETIENE LOS CONTAINERS
