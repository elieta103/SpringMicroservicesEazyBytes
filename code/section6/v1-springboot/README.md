## Seccion 06 v1 


### Externalizar configuracion en Spring Boot
0. Para generar las imagenes de docker en los 3 MS, se sigue el enfoque de Jib-Google

1. Using @Value annotation
```
build:
  version: "3.0" 
  
    @Value("${build.version}")
    private String buildVersion;
```
2. Using Environment (Variables de entorno)
```
    @Autowired
    private Environment environment;

    public void getProperty(){
        String propertValue = environment.getProperty("JAVA_HOME");
    }
```

3. Using @ConfigurationProperties
```
@ConfigurationProperties(prefix = "accounts")
public record AccountsContactInfoDto(String message, Map<String, String> contactDetails, List<String> onCallSupport) {
}

accounts:
  message: "Welcome to EazyBank accounts related local APIs "
  contactDetails:
    name: "John Doe - Developer"
    email: "john@eazybank.com"
  onCallSupport:
    - (555) 555-1234
    - (555) 523-1345
    
    SpringApplication.class -> @EnableConfigurationProperties(value = {AccountsContactInfoDto.class})

    @Autowired
    private AccountsContactInfoDto accountsContactInfoDto;
    
    
    @GetMapping("/contact-info")
    public ResponseEntity<AccountsContactInfoDto> getContactInfo() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(accountsContactInfoDto);
    }
```

### Profiles in Spring Boot
1. Crear archivos de propiedades:
  - application_prod.yml
  - application_qa.yml

2. Analizar que propiedades cambian de un ambiente a otro, y modificarlas.

3. Activar las propiedades en cada profile application_qa.yml, application_prod.yml
```
spring:
  config:
    activate:
      on-profile: "prod"
```

4. Listar en la configuración default, cuales son las probables configuraciones
   y definir la actual.
```
spring:
  config:
    import:
      - "application_qa.yml"
      - "application_prod.yml"
  profiles:
    active:
      - "qa"
```

### Profiles en Spring Boot, Desde la línea de commandos
- ...\accounts> mvn clean package
- ...\accounts\target> java -jar accounts-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod --build.version="1.1" 

### Profiles en Spring Boot, Desde la IDE
- Click Izquierdo en AccountsApplication
- Modify Run Configurations :
  -Dspring.profiles.active=prod -Dbuild.version="1.1"
  SPRING_PROFILES_ACTIVE=prod;BUILD_VERSION=1.1






