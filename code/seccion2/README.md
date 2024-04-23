## Seccion 02 Creacion de servicios


### Servicio Accounts
- Utiliza H2 Database
- MapStruct & ModelMapper, no son oficialmente recomendadas por Spring
- Manejo de transacción cuando se borran los datos(Accounts)

- Manejo de excepciones con GlobalHandlerException
  - @ControllerAdvice, @ExceptionHandler(Exception.class)...
  ```
      @Transactional
      @Modifying
      void deleteByCustomerId(Long customerId);
  ```
  - Dentro del ExceptionHandler, puede ir cualquier lógica
  - Para testear las RuntimeException , se comenta : @AllArgsConstructor y se envia alguna peticion.

- Manejo de Validaciones
  - Dependencia : spring-boot-starter-validation
  - DTO's : @NotEmpty, @Size, @Email, @Pattern 
  - Controller nivel classe : @Validated
  - Controller param metodos: @Valid @RequestBody  DTO's  (Orden es importante)
  - Controller un solo param: @RequestParam @Pattern(...)
  - Mensajes : GlobalExceptionHandler  extends ResponseEntityExceptionHandler
  - Sobreescribit : protected ResponseEntity<Object> handleMethodArgumentNotValid{...}

- Auditar datos
  - En la BD existen estas columnas : CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY
  - BaseEntity agregar :   @CreatedDate, @CreatedBy, @LastModifiedDate, @LastModifiedBy
  - Para devolver quien realiza la creación o update :
    - public class AuditAwareImpl implements AuditorAware<String> {...}
  - BaseEntity agregar : @EntityListeners(AuditingEntityListener.class)
  - Main class AccountsApplication agregar : @EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")

- Documentacion
  - http://localhost:8080/swagger-ui/index.html
  - Dependencia : springdoc-openapi-starter-webmvc-ui
  - En AccountsApplication :  @OpenAPIDefinition(...)
  - En Controllers : @Tag(...) , @Operation(...), @ApiResponses(...)
  - En DTO's @Schema
  - En los @ApiResponse del controller se agrega la informacion del ErrorResponseDTO