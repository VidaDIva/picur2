# AGENTS.md

Spring Boot 4.1.1 REST API (`com.picur.demo`) for "picur", a vibration-monitoring system. Java 21, Maven (wrapper only - no global `mvn`). Frontend is a separate app served on `http://localhost:4321`.

## Commands (Windows - Maven wrapper only)

- Build: `.\mvnw.cmd clean package`
- Run server: `.\mvnw.cmd spring-boot:run` (runs on default port 8080)
- Tests: `.\mvnw.cmd test`
- No global Maven is installed; always use `mvnw.cmd`, never `mvn`.

## Runtime prerequisite (critical)

- The app requires a running MySQL server. Credentials come from environment variables (defaults: user `root`, empty password), loaded automatically from `picur2/.env` via `spring.config.import`:
  - URL: `jdbc:mysql://localhost:3306/picur?createDatabaseIfNotExist=true` (the DB **is** auto-created by the JDBC URL)
  - `PICUR_DB_USER`, `PICUR_DB_PASSWORD` (copy `.env.example` to `.env`; `.env` is gitignored)
  - Mail and VAPID secrets also live in `.env` (`PICUR_MAIL_*`, `PICUR_VAPID_*`)
- Without a reachable database, `./mvnw.cmd test` fails, because `DemoApplicationTests` is a `@SpringBootTest` that boots the full context.
- There are no migration/flyway files; schema changes happen automatically via `ddl-auto=update`.

## Architecture / conventions

- Layered: `controller/` -> `service/` (interface) + `service/impl/` (**implementations go in `service/impl`, not alongside the interface**) -> `repository/` -> `model/`.
- DTOs are Java **records** in `DTO/Request` and `DTO/Respo` (the "Respo" package name is intentionally as-is - a typo; do not rename without updating all imports). Mappers are static utility classes in `mapper/` (`toEntity(request)` / `dto(entity)`).
- Full package is `com.picur.demo`; entities, columns, DTO fields, and code comments are in Spanish (e.g. `contrasena`, `fechaRegistro`, `aceleracionX`). Match this style.
- Endpoints: `/api/usuarios` (account CRUD), `/api/mediciones` (vibration readings; `estado` always set to `NORMAL`, `indiceAnomalia` to `0.0` on create), and `/api/sensores/medicion` (Pico W ingestion; expects `dispositivo`, `timestamp` ISO-8601, `aceleracionX/Y/Z`, plus optional `frecuencia`, `rms`, `inclinacion`, `vibracion`, `umbral`, `estado`; returns `201 {success, message, vibracion, umbral, frecuencia, rms, inclinacion, estado, indiceAnomalia}`; timestamp sanitized server-side - falls back to `LocalDateTime.now()` if year is implausible; persisted into `MedicionVibracion` via `MedicionVibracionSensorMapper`). **The device is the source of truth for `estado`** when it sends it: mapped `NORMAL->NORMAL`, `ANOMALIA->PRECAUCION`, `PELIGRO->ANOMALA` (`MedicionVibracionServiceImpl.mapearEstadoDispositivo`); the server-side analyzer (`AnalizadorVibracionServiceImpl`) still runs to fill `indiceAnomalia` and provides the fallback `estado` for payloads that omit it (old firmware). `vibracion`/`umbral` are new entity columns created by `ddl-auto=update`.
- Service logs are SLF4J: console plus `logs/picur.log` (rotating daily, 10 MB max, 7 days kept). `SensorController`, `MedicionVibracionServiceImpl`, `GlobalExceptionHandler`, and `VibracionWebSocketHandler` log device, IP, timestamp, status, vibration, index, latency, and errors. SQL logging is disabled. **Never log passwords, tokens, or full request bodies.**
- LAN discovery: `config/DescubrimientoServidor` listens on **UDP 5001** and answers `PICUR_DISCOVER_V1` with `{"servicio":"picur","version":1,"puerto":8080,"ips":[...]}`. The Pico uses this first, then its stored `pc_ip`/`PC_IP` fallback. Windows Firewall must allow TCP 8080 and UDP 5001 on the same subnet.
- Security (`config/SecurityConfig.java`): stateless, CSRF disabled, **all routes currently `permitAll`**; account passwords are BCrypt-encoded. CORS is global in `config/CorsConfig.java` via `picur.cors.allowed-origins` (`PICUR_CORS_ORIGINS`), so controllers no longer use `@CrossOrigin`; WebSocket origins use `picur.websocket.allowed-origins` (`PICUR_WS_ORIGINS`). The Pico W posts to the PC's LAN IP (`http://<PC_IP>:8080/api/sensores/medicion`), never `localhost`; the LAN IP can change, so prefer discovery or `deploy_pico.ps1` over editing code.
- Web Push: `NotificacionPushService` sends VAPID notifications with `dev.blanke.webpush` (BouncyCastle + jose4j) to rows in `suscripciones_push`; endpoints live under `/api/push` (`GET /clave-publica`, `POST/DELETE /suscripciones`). `NotificacionRecomendacionService` calls it after sending alert emails. Push is disabled (warn on startup) when `PICUR_VAPID_*` is missing. Subscriptions are keyed by `endpoint` and store `usuarioId`, `p256dh`, `auth`, and `userAgent`.
- Beware Spring Boot 4 starter grouping: `spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-validation-test` in `pom.xml` - not the classic `web`, `test`, etc. artifact IDs.

## Raspberry Pi Pico W firmware (`firmware/`)

- MicroPython modules deployed flat to the Pico filesystem (`main.py`, `config.py`, `logger.py`, `descubrimiento.py`, `wifi_manager.py`, `mpu6050.py`, `api_client.py`, `urequests.py`, `procesar.py`, `clasificador.py`, `piezo.py`, `semaforo.py`, `sirena.py`, `lcd_i2c.py`). `firmware/pruebas/` holds scripts run with `mpremote run` but never deployed: `detectar.py` (hardware self-test: I2C/ADC/LEDs/PWM), `escanear_i2c.py` (scans alternate I2C pin combos), `enviar_prueba.py` (Wi-Fi + POST smoke test without the MPU).
- Configuration/deploy: run `.\deploy_pico.ps1` from the repo root. It detects the current SSID/IP/password, merges that network into `firmware/redes.local.json` (**ignored by git; contains credentials**), generates a temp `config.py` as UTF-8 without BOM, flashes it with `:/config.py.bak` rollback, copies all firmware modules, and resets. `.\flash_config.ps1` is the config-only wrapper (`-SkipFirmware -NoReset`) and `-Run` starts `main.py` over USB. Both delegate to `picur_red.ps1`.
- `config.py` supports multiple known networks via `REDES = [{"ssid": ..., "password": ..., "pc_ip": ...}]`; the current network is kept first. The Pico remembers only networks flashed this way. It cannot guess a new network password, and the Pico W radio is **2.4 GHz only**; add a new network by re-running `deploy_pico.ps1` over USB on that network.
- New peripherals: piezo on GP26/ADC0, traffic-light LEDs on GP13/14/15, siren PWM on GP16 (see `main.py` header pinout). LCD I2C is optional/tolerant.
- MPU-6050 is on **I2C0** (`machine.I2C(0, scl=Pin(5), sda=Pin(4))`) - GP4/GP5 belong to I2C0, not I2C1; wrong bus => `ValueError: bad SCL pin`. Sensor address `0x68` (AD0->GND). `mpu6050.MPU6050()` raises `OSError` at startup if the sensor is absent from `i2c.scan()`.
- MicroPython quirks to remember: **no `**` dict unpacking in dict literals** (`SyntaxError`), remote `urequests.py`/`mpremote run` fail on bare status lines like `HTTP/1.1 200 ` (fixed in `firmware/urequests.py`), and **`socket.settimeout()` is in seconds, not milliseconds** (the old `settimeout(2000)` meant ~33 min and blocked the POST until the 8 s watchdog reset the Pico; use `1.5`).
- Deploy individual files: `python -m mpremote connect com8 cp firmware/<file>.py :/<file>.py`; console: `python -m mpremote connect com8 repl`; reset: `python -m mpremote connect com8 reset`. The Pico is normally on `COM8`; `mpremote` is installed globally, not in this repo. Reading `:/picur.log` with `mpremote cp` interrupts the running loop, so reset afterward.
- Default `main.py` loop: multi-network Wi-Fi (`wifi_manager.conectar`) -> NTP time sync (`logger.sincronizar_hora`, so timestamps are real) -> startup self-test (LEDs + siren beep) -> ~10 s baseline calibration (`clasificador.calibrar_linea_base`, yellow LED blinking, no POSTs) -> UDP server discovery (`descubrimiento.obtener_servidor`, cached with TTL and `pc_ip` fallback) -> continuous 100 ms measurement windows (`procesar.leer_ventana`: MPU + piezo at ~200 Hz) that classify NORMAL/ANOMALIA/PELIGRO locally (`clasificador.py`: dynamic thresholds `media + k*std`, persistence counters, siren latch) -> POST every `PERIODO_ENVIO_S` seconds with the latest clean window (`dispositivo`, real timestamp, accel/rms/frecuencia/inclinacion, plus `vibracion`, `umbral`, `estado`). The Pico's classification is the source of truth sent to Spring Boot; LEDs/siren update instantly on-device.
- Pico logging (`logger.py`): USB serial plus `/picur.log` for startup, Wi-Fi, NTP, discovery, state changes, alerts, and a 60 s summary (`ok`, `fallidos`, network, IP). It does **not** write every 2 s send to flash; only event/error lines are persisted. `logger.buffer` holds recent lines for diagnostics.

## Other

- No CI, linters, or formatters configured. `README.md` is empty. No codegen beyond the checked-in Maven wrapper (`/mvnw` is LF, `*.cmd` is CRLF via `.gitattributes`).
- Errors are thrown as bare `RuntimeException` with Spanish messages (no custom exception handling yet); keep that pattern in new services.
