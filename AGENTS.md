# AGENTS.md

Spring Boot 4.1.1 REST API (`com.picur.demo`) for "picur", a vibration-monitoring system. Java 21, Maven (wrapper only — no global `mvn`). Frontend is a separate app served on `http://localhost:4321`.

## Commands (Windows — Maven wrapper only)

- Build: `.\mvnw.cmd clean package`
- Run server: `.\mvnw.cmd spring-boot:run` (runs on default port 8080)
- Tests: `.\mvnw.cmd test`
- No global Maven is installed; always use `mvnw.cmd`, never `mvn`.

## Runtime prerequisite (critical)

- The app requires a running MySQL server. Credentials are hardcoded in `src/main/resources/application.properties`:
  - URL: `jdbc:mysql://localhost:3306/picur?createDatabaseIfNotExist=true` (the DB **is** auto-created by the JDBC URL)
  - user `root`, password `10232004`
- Without a reachable database, `./mvnw.cmd test` fails, because `DemoApplicationTests` is a `@SpringBootTest` that boots the full context.
- There are no migration/flyway files; schema changes happen automatically via `ddl-auto=update`.

## Architecture / conventions

- Layered: `controller/` → `service/` (interface) + `service/impl/` (**implementations go in `service/impl`, not alongside the interface**) → `repository/` → `model/`.
- DTOs are Java **records** in `DTO/Request` and `DTO/Respo` (the "Respo" package name is intentionally as-is — a typo; do not rename without updating all imports). Mappers are static utility classes in `mapper/` (`toEntity(request)` / `dto(entity)`).
- Full package is `com.picur.demo`; entities, columns, DTO fields, and code comments are in Spanish (e.g. `contrasena`, `fechaRegistro`, `aceleracionX`). Match this style.
- Endpoints: `/api/usuarios` (account CRUD), `/api/mediciones` (vibration readings; `estado` always set to `NORMAL`, `indiceAnomalia` to `0.0` on create), and `/api/sensores/medicion` (Pico W ingestion; expects `dispositivo`, `timestamp` ISO-8601, `aceleracionX/Y/Z`, plus optional `frecuencia`, `rms`, `inclinacion`, `vibracion`, `umbral`, `estado`; returns `201 {success, message, vibracion, umbral, frecuencia, rms, inclinacion, estado, indiceAnomalia}`; timestamp sanitized server-side — falls back to `LocalDateTime.now()` if year is implausible; persisted into `MedicionVibracion` via `MedicionVibracionSensorMapper`). **The device is the source of truth for `estado`** when it sends it: mapped `NORMAL→NORMAL`, `ANOMALIA→PRECAUCION`, `PELIGRO→ANOMALA` (`MedicionVibracionServiceImpl.mapearEstadoDispositivo`); the server-side analyzer (`AnalizadorVibracionServiceImpl`) still runs to fill `indiceAnomalia` and provides the fallback `estado` for payloads that omit it (old firmware). `vibracion`/`umbral` are new entity columns created by `ddl-auto=update`.
- Security (`config/SecurityConfig.java`): stateless, CSRF disabled, **all routes currently `permitAll`**; account passwords are BCrypt-encoded. `@CrossOrigin(origins = "http://localhost:4321")` on controllers. The Pico W posts to the PC's LAN IP (e.g. `http://192.168.1.4:8080/api/sensores/medicion`), never `localhost`.
- Beware Spring Boot 4 starter grouping: `spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-validation-test` in `pom.xml` — not the classic `web`, `test`, etc. artifact IDs.

## Raspberry Pi Pico W firmware (`firmware/`)

- MicroPython modules deployed flat to the Pico filesystem (`main.py`, `config.py`, `wifi_manager.py`, `mpu6050.py`, `api_client.py`, `urequests.py`, `procesar.py`, `clasificador.py`, `piezo.py`, `semaforo.py`, `sirena.py`). `firmware/pruebas/` holds scripts run with `mpremote run` but never deployed: `detectar.py` (hardware self-test: I2C/ADC/LEDs/PWM), `escanear_i2c.py` (scans alternate I2C pin combos), `enviar_prueba.py` (Wi-Fi + POST smoke test without the MPU).
- `flash_config.ps1` (repo root) is the proper way to configure Wi-Fi: it detects the PC's current SSID/IP/password, generates `config.py` in a temp file, and flashes it to the device — it does not touch the repo's `firmware/config.py`. The repo's `firmware/config.py` currently holds working credentials anyway (SSID/password/PC_IP drift when the network changes; re-run `flash_config.ps1`).
- New peripherals: piezo on GP26/ADC0, traffic-light LEDs on GP13/14/15, siren PWM on GP16 (see `main.py` header pinout).
- MPU-6050 is on **I2C0** (`machine.I2C(0, scl=Pin(5), sda=Pin(4))`) — GP4/GP5 belong to I2C0, not I2C1; wrong bus => `ValueError: bad SCL pin`. Sensor address `0x68` (AD0→GND). `mpu6050.MPU6050()` raises `OSError` at startup if the sensor is absent from `i2c.scan()`.
- MicroPython quirks to remember: **no `**` dict unpacking in dict literals** (`SyntaxError`), and remote `urequests.py`/`mpremote run` fail on bare status lines like `HTTP/1.1 200 ` (fixed in `firmware/urequests.py`).
- Deploy/run from PowerShell: `python -m mpremote connect com8 cp firmware/<file>.py :/<file>.py` and `python -m mpremote connect com8 run firmware/main.py` (ctrl via `mpremote connect com8 reset`). The Pico is on `COM8`; `mpremote` is installed globally, not in this repo.
- Default `main.py` loop: Wi-Fi → startup self-test (LEDs + siren beep) → ~10 s baseline calibration (`clasificador.calibrar_linea_base`, yellow LED blinking, no POSTs) → continuous 100 ms measurement windows (`procesar.leer_ventana`: MPU + piezo at ~200 Hz) that classify NORMAL/ANOMALIA/PELIGRO locally (`clasificador.py`: dynamic thresholds `media + k*std`, persistence counters, siren latch) → POST every `PERIODO_ENVIO_S` seconds with the latest clean window (`dispositivo`, `timestamp` from un-synced RTC so it sends `2021-01-01` until NTP is added, accel/rms/frecuencia/inclinacion, plus `vibracion`, `umbral`, `estado`). The Pico's classification is the source of truth sent to Spring Boot; LEDs/siren update instantly on-device.

## Other

- No CI, linters, or formatters configured. `README.md` is empty. No codegen beyond the checked-in Maven wrapper (`/mvnw` is LF, `*.cmd` is CRLF via `.gitattributes`).
- Errors are thrown as bare `RuntimeException` with Spanish messages (no custom exception handling yet); keep that pattern in new services.