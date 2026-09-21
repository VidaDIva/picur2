import time

import config
import wifi_manager
import mpu6050
import api_client
import procesar


def timestamp_iso():
    ahora = time.localtime()
    return "{:04d}-{:02d}-{:02d}T{:02d}:{:02d}:{:02d}".format(*ahora[:6])


def main():
    if not wifi_manager.conectar(config.WIFI_SSID, config.WIFI_PASSWORD):
        print("[main] Sin conexion Wi-Fi. Reinicia el Pico.")
        return

    mpu = mpu6050.MPU6050()
    print("[main] MPU-6050 listo")

    while True:
        try:
            muestras, fs = procesar.capturar_burst(mpu, config.NUM_MUESTRAS)
            media_x, media_y, media_z = procesar.media_vector(muestras)
            rms = procesar.calcular_rms(muestras)
            frecuencia = procesar.calcular_frecuencia(muestras, fs)
            inclinacion = procesar.calcular_inclinacion(media_x, media_y, media_z)

            payload = {
                "dispositivo": config.DISPOSITIVO,
                "timestamp": timestamp_iso(),
                "aceleracionX": media_x,
                "aceleracionY": media_y,
                "aceleracionZ": media_z,
                "frecuencia": frecuencia,
                "rms": rms["rms"],
                "inclinacion": inclinacion,
            }
            ok, cuerpo = api_client.enviar_medicion(payload)
            print("[main] fs={:.1f} Hz | envio:".format(fs), ok, payload)
        except Exception as e:
            print("[main] Error:", e)
        time.sleep(config.PERIODO_ENVIO_S)


if __name__ == "__main__":
    main()