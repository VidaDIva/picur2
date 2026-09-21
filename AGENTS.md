# AGENTS.md

Spring Boot 4.1.1 REST API (`com.picur.demo`) for "picur", a vibration-monitoring system. Java 21, Maven (wrapper only — no global `mvn`). Frontend is a separate app served on `http://localhost:4321`.

## Commands (Windows — Maven wrapper only)

- Build: `.\mvnw.cmd clean package`
- Run server: `.\mvnw.cmd spring-boot:run` (runs on default port 8080)
- Tests: `.\mvnw.cmd test`
- No global Maven is installed; always use `mvnw.cmd`, never `mvn`.

## Runtime prerequisite (critical)

- The app requires a running MySQL server. Credentials are hardcoded in `src/main/resources/application.properties`:
  - URL: `jdbc:mysql://localhost:3306/picur?createDatabaseIfNotExist=false`
  - user `root`, password `10232004`
- The `picur` database must already exist — Hibernate will not create it (`createDatabaseIfNotExist=false`), it only applies schema via `ddl-auto=update`. Without a reachable database, `./mvnw.cmd test` fails, because `DemoApplicationTests` is a `@SpringBootTest` that boots the full context.
- There are no migration/flyway files; schema changes happen automatically via `ddl-auto=update`.

## Architecture / conventions

- Layered: `controller/` → `service/` (interface) + `service/impl/` (**implementations go in `service/impl`, not alongside the interface**) → `repository/` → `model/`.
- DTOs are Java **records** in `DTO/Request` and `DTO/Respo` (the "Respo" package name is intentionally as-is — a typo; do not rename without updating all imports). Mappers are static utility classes in `mapper/` (`toEntity(request)` / `dto(entity)`).
- Full package is `com.picur.demo`; entities, columns, DTO fields, and code comments are in Spanish (e.g. `contrasena`, `fechaRegistro`, `aceleracionX`). Match this style.
- Endpoints: `/api/usuarios` (account CRUD), `/api/mediciones` (vibration readings; `estado` always set to `NORMAL`, `indiceAnomalia` to `0.0` on create), and `/api/sensores/medicion` (Pico W ingestion; expects `dispositivo`, `timestamp` ISO-8601, `aceleracionX/Y/Z`; returns `201 {success, message}`; timestamp sanitized server-side — falls back to `LocalDateTime.now()` if year is implausible; persisted into `MedicionVibracion` via `MedicionVibracionSensorMapper`).
- Security (`config/SecurityConfig.java`): stateless, CSRF disabled, **all routes currently `permitAll`**; account passwords are BCrypt-encoded. `@CrossOrigin(origins = "http://localhost:4321")` on controllers. The Pico W posts to the PC's LAN IP (e.g. `http://192.168.1.4:8080/api/sensores/medicion`), never `localhost`.
- Beware Spring Boot 4 starter grouping: `spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-validation-test` in `pom.xml` — not the classic `web`, `test`, etc. artifact IDs.

## Raspberry Pi Pico W firmware (`firmware/`)

- MicroPython modules deployed flat to the Pico filesystem (`main.py`, `config.py`, `wifi_manager.py`, `mpu6050.py`, `api_client.py`, `urequests.py`). `firmware/pruebas/` holds phase scripts that are run but not deployed.
- `config.py` in the repo has **placeholder Wi-Fi credentials only** (private creds are written only on the device, never committed). Contains `PC_IP`/`PUERTO` pointing at the Spring Boot host.
- MPU-6050 is on **I2C0** (`machine.I2C(0, scl=Pin(5), sda=Pin(4))`) — GP4/GP5 belong to I2C0, not I2C1; wrong bus => `ValueError: bad SCL pin`. Sensor address `0x68` (AD0→GND).
- MicroPython quirks to remember: **no `**` dict unpacking in dict literals** (`SyntaxError`), and remote `urequests.py`/`mpremote run` fail on bare status lines like `HTTP/1.1 200 ` (fixed in `firmware/urequests.py`).
- Deploy/run from PowerShell: `python -m mpremote connect com8 cp firmware/<file>.py :/<file>.py` and `python -m mpremote connect com8 run firmware/main.py` (ctrl via `mpremote connect com8 reset`). The Pico is on `COM8`; `mpremote` is installed globally, not in this repo.
- Default `main.py` loop: read MPU-6050 → build payload (`dispositivo`, `timestamp` from un-synced RTC, so it sends `2021-01-01` until NTP is added) → POST every `PERIODO_ENVIO_S` seconds.

## Other

- No CI, linters, or formatters configured. `README.md` is empty. No codegen beyond the checked-in Maven wrapper (`/mvnw` is LF, `*.cmd` is CRLF via `.gitattributes`).
- Errors are thrown as bare `RuntimeException` with Spanish messages (no custom exception handling yet); keep that pattern in new services.