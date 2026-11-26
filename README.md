# ms-autenticacion

Microservicio de **identidad y seguridad** del caso BodegaNube (JVY0101). Administra los usuarios
(operarios de bodega y comercios), guarda sus contraseñas con BCrypt y emite el JWT que valida el gateway.

| | |
|---|---|
| Puerto | `8081` |
| Base de datos | PostgreSQL `bodeganube_auth` (una base por microservicio) |
| Stack | Java 17, Spring Boot 3.2, Spring Data JPA, JJWT, Spring Security Crypto (BCrypt), Maven Wrapper |

## Requerimientos que cubre
- **RF-01**: autenticación y autorización granular según el rol (`OPERARIO` o `COMERCIO`).
- **RF-06**: el usuario `COMERCIO` queda asociado a su `comercioId`, que viaja en el JWT para que cada
  comercio vea solo sus órdenes.

## Seguridad
- Las contraseñas se guardan como **hash BCrypt** (con sal y lento a propósito); nunca en texto plano y
  nunca se devuelven en la API.
- El login responde el mismo 401 si el usuario no existe o si la contraseña está mal, para no revelar qué
  usernames existen.
- El JWT se firma con HMAC usando `JWT_SECRET`, lleva los claims `rol` y `comercioId` y expira en 1 hora.

## Arquitectura en capas
```
src/main/java/com/bodeganube/autenticacion
├── controller/   AuthController, UsuarioController   reciben HTTP, validan con @Valid y delegan
├── service/      AuthService, UsuarioService         login, alta y cambios de usuario (@Transactional)
├── security/     JwtService                          firma del JWT con claims de rol y comercio
├── config/       SeguridadConfig                     bean PasswordEncoder (BCrypt)
├── repository/   UsuarioRepository                   acceso a datos con Spring Data JPA
├── model/        Usuario, Rol                        entidad JPA mapeada a la tabla usuarios
├── dto/          *Request / *Response                contrato JSON de la API, sin la contraseña
└── exception/    GlobalExceptionHandler              errores con formato JSON único (incluye 401)
```

## Modelo de datos
Tabla `usuarios` (entidad `Usuario`). Hibernate la crea al iniciar (`ddl-auto: update`).

| Columna | Tipo | Restricciones |
|---|---|---|
| id | bigint | PK, autoincremental |
| username | varchar | NOT NULL, UNIQUE |
| password | varchar | NOT NULL, hash BCrypt |
| rol | varchar | NOT NULL: `OPERARIO` o `COMERCIO` |
| comercio_id | varchar | obligatorio si el rol es `COMERCIO`; nulo para `OPERARIO` |

## Endpoints
Base: `http://localhost:8081`. A través de ms-gateway (`http://localhost:8080`) el login es público y el CRUD de usuarios pide el JWT de un `OPERARIO`.

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Valida credenciales y devuelve el JWT | 200, 400, 401 |
| GET | `/api/v1/usuarios` | Lista usuarios | 200 |
| GET | `/api/v1/usuarios/{id}` | Obtiene un usuario | 200, 404 |
| POST | `/api/v1/usuarios` | Crea un usuario | 201 con `Location`, 400, 409 si el username existe |
| PUT | `/api/v1/usuarios/{id}` | Cambia rol, comercio y, opcionalmente, la contraseña | 200, 400, 404, 409 |
| DELETE | `/api/v1/usuarios/{id}` | Elimina un usuario | 204, 404 |

Crear un usuario y luego iniciar sesión:
```json
POST /api/v1/usuarios
{ "username": "tienda-sur", "password": "clave-segura-123", "rol": "COMERCIO", "comercioId": "comercio-123" }

POST /api/v1/auth/login
{ "username": "tienda-sur", "password": "clave-segura-123" }
→ { "token": "eyJhbGciOiJIUzM4NCJ9...", "tokenType": "Bearer" }
```
El token se puede inspeccionar en [jwt.io](https://jwt.io) para ver los claims `rol` y `comercioId`.

Todas las respuestas de error tienen el mismo formato:
```json
{
  "timestamp": "2026-10-08T18:30:12.448",
  "status": 401,
  "error": "Unauthorized",
  "mensaje": "Usuario o contrasena incorrectos",
  "ruta": "/api/v1/auth/login"
}
```

## Usuarios iniciales
Al arrancar se crean, solo si no existen, dos usuarios de demostración (`UsuariosIniciales`). Sin ellos nadie
podría hacer el primer login, porque el gateway exige JWT para toda la API:

| Usuario | Rol | comercioId | Contraseña (variable) |
|---|---|---|---|
| `operario` | `OPERARIO` | — | `OPERARIO_PASSWORD` (por defecto `Operario.2026`) |
| `tienda1` | `COMERCIO` | `comercio-123` | `COMERCIO_PASSWORD` (por defecto `Tienda1.2026`) |

En el clúster las contraseñas vienen de secretos de Docker Swarm.

## Levantar el servicio desde cero

### 1. Requisitos
- JDK 17 o superior (`java -version`).
- PostgreSQL 14 o superior en `localhost:5432` (usuario y contraseña `postgres` por defecto).
- Git. **No hace falta instalar Maven**: el repositorio trae el Maven Wrapper (`mvnw` y `mvnw.cmd`).

### 2. Clonar el repositorio
```bash
git clone https://github.com/Caso-Bodega/ms-autenticacion.git
cd ms-autenticacion
```

### 3. Crear la base de datos
Con psql:
```bash
psql -U postgres -c "CREATE DATABASE bodeganube_auth;"
```
O en pgAdmin: clic derecho en *Databases* → *Create* → *Database...* → `bodeganube_auth`.
Las tablas las crea la aplicación al iniciar.

Si no tienes PostgreSQL instalado, puedes usar Docker:
```bash
docker run -d --name bodeganube-db -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16
docker exec bodeganube-db psql -U postgres -c "CREATE DATABASE bodeganube_auth;"
```

### 4. Compilar, probar y empaquetar
```bash
.\mvnw.cmd clean package    # Windows (PowerShell)
./mvnw clean package        # Linux, macOS o Git Bash
```
Descarga las dependencias, compila, corre las pruebas automatizadas y genera `target/ms-autenticacion.jar`.

### 5. Ejecutar
```bash
java -jar target/ms-autenticacion.jar
```
También se puede levantar sin empaquetar con `.\mvnw.cmd spring-boot:run`. Queda escuchando en
`http://localhost:8081`.

### 6. Probar con Postman
Importa `postman/ms-autenticacion.postman_collection.json` (*Import* → archivo) y usa *Run collection*.
Ejecuta en orden 12 peticiones (crear usuario, login correcto e incorrecto, consultar, actualizar y
eliminar, más los casos de error 400, 401, 404 y 409) y cada una verifica su código HTTP.

## Configuración
Todo tiene un valor por defecto para desarrollo local y se puede cambiar con variables de entorno:

| Variable | Valor por defecto |
|---|---|
| `PORT` | `8081` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bodeganube_auth` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `JWT_SECRET` | clave de desarrollo (mínimo 32 caracteres; en un ambiente real se debe cambiar) |
| `JWT_EXPIRATION_MS` | `3600000` (1 hora) |
| `OPERARIO_PASSWORD` | `Operario.2026` |
| `COMERCIO_PASSWORD` | `Tienda1.2026` |

## Pruebas automatizadas
`.\mvnw.cmd test` corre 18 pruebas que no necesitan base de datos:
- `AuthServiceTest` y `UsuarioServiceTest` (JUnit 5 + Mockito, con BCrypt real): login, hash de la
  contraseña, username repetido y reglas del rol `COMERCIO`.
- `AuthControllerTest` y `UsuarioControllerTest` (MockMvc): 200, 201, 400, 401, 404 y que la contraseña
  nunca aparezca en la respuesta.
- `UsuariosInicialesTest`: crea los usuarios de demostración con la contraseña cifrada y no los duplica.

## Docker
La imagen se construye con el `Dockerfile` de la raíz, en dos etapas:

| Etapa | Imagen base | Qué hace |
|---|---|---|
| build | `eclipse-temurin:17-jdk-alpine` | Baja las dependencias (capa que se reutiliza mientras no cambie `pom.xml`), compila y separa el `.jar` en capas |
| final | `eclipse-temurin:17-jre-alpine` | Solo el JRE y la aplicación en 4 capas, ejecutada por el usuario sin privilegios `bodega` |

Buenas prácticas aplicadas: imagen final de unos 110 MB sin JDK ni código fuente, `.dockerignore` para que el
contexto de build sea chico, usuario no root, `HEALTHCHECK` contra `/actuator/health/liveness`, apagado
ordenado (`server.shutdown: graceful`) y la JVM ajustada al límite de memoria del contenedor
(`-XX:MaxRAMPercentage`).

```bash
docker build -t ms-autenticacion .
docker run -p 8081:8081 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/bodeganube_auth ms-autenticacion
```
Para levantar el sistema completo (5 microservicios + PostgreSQL) se usa `docker compose up -d --build` en
[bodeganube-infra](https://github.com/Caso-Bodega/bodeganube-infra). En Docker Swarm las contraseñas llegan como
secretos en `/run/secrets` y Spring las lee con `spring.config.import=optional:configtree:/run/secrets/`.

## CI/CD (GitHub Actions)
Pipeline en `.github/workflows/ci-cd.yml`:

| Evento | Etapas |
|---|---|
| Pull request a `develop` o `main` | **1. Build y test**: `./mvnw verify` con JDK 17; el reporte de pruebas queda como artefacto |
| Push a `develop` | 1 + **2. Imagen**: publica `ghcr.io/caso-bodega/ms-autenticacion:develop` en GitHub Container Registry |
| Push a `main` (merge del PR) | 1 + 2 (etiquetas `latest` y `sha-<commit>`) + **3. Despliegue**: `docker service update` en el clúster Swarm de AWS (rolling update con rollback automático) y verificación de `http://<manager>:8080/estado/ms-autenticacion` |

La etiqueta `sha-<commit>` dice exactamente qué commit corre en el clúster (trazabilidad). La etapa 3 usa los
secretos de la organización `SWARM_HOST` y `SWARM_SSH_KEY`; si no existen, se omite con una advertencia.

## Ramas
`main` tiene la versión entregada, `develop` integra el trabajo en curso y cada cambio entra desde una
rama `feature/...` con un Pull Request hacia `develop`.

## Próximos pasos
- Agregar un rol `ADMIN` para el CRUD de usuarios (hoy, a través del gateway, lo usa el `OPERARIO`).
- Firmar los JWT con RSA (RS256) y publicar la llave pública, para que Amazon API Gateway también pueda
  validarlos con un *JWT authorizer*.
