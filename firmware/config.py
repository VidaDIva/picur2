# ============ Configuracion del Pico W ============
# Plantilla: lo normal es que deploy_pico.ps1 genere la config del
# dispositivo segun la red actual. Este archivo solo se copia al Pico
# si se hace manualmente.

WIFI_SSID = "FERNANDEZ ORREGO"
WIFI_PASSWORD = "Jd1137974378"
PC_IP = "192.168.1.5"

# Redes conocidas. El Pico prueba la ultima que funciono, luego las
# visibles y por ultimo el resto. pc_ip es un respaldo si falla UDP.
REDES = [
    {
        "ssid": WIFI_SSID,
        "password": WIFI_PASSWORD,
        "pc_ip": PC_IP,
    },
]

PUERTO = 8080

DISPOSITIVO = "PICO-001"
PERIODO_ENVIO_S = 2

NUM_MUESTRAS = 128

# Descubrimiento automatico del servidor Spring en la red actual.
DESCUBRIR_SERVIDOR = True
PUERTO_DESCUBRIMIENTO = 5001
ARCHIVO_SERVIDOR = "/servidor.json"
TTL_SERVIDOR_MS = 60000
COOLDOWN_DESCUBRIMIENTO_MS = 15000

# Logs: eventos/errores a flash y todo a USB serial.
LOG_NIVEL = "INFO"
LOG_ARCHIVO = "/picur.log"
LOG_ARCHIVO_HABILITADO = True
LOG_MAX_BYTES = 8192
TZ_OFFSET_S = -5 * 3600
