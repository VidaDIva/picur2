from machine import Pin, I2C
from time import sleep_ms

ADDR = 0x68


def intento(freq):
    try:
        i2c = I2C(0, sda=Pin(4), scl=Pin(5), freq=freq)
    except Exception as e:
        print("{} Hz: error abriendo bus: {}".format(freq, e))
        return
    scan = i2c.scan()
    print("{} Hz: scan={}".format(freq, scan))
    if ADDR not in scan:
        return
    for nombre, fn in (
            ("readfrom_mem WHO_AM_I", lambda: i2c.readfrom_mem(ADDR, 0x75, 1)),
            ("writeto+readfrom_mem", lambda: (i2c.writeto(ADDR, b"\x75"),
                                               i2c.readfrom(ADDR, 1))),
            ("readfrom_mem PWR (0x6B)", lambda: i2c.readfrom_mem(ADDR, 0x6B, 1)),
    ):
        try:
            r = fn()
            print("   OK  {} -> {}".format(nombre, r))
        except Exception as e:
            print("   ERR {} -> {}".format(nombre, e))
    # despertar y leer accel
    try:
        i2c.writeto_mem(ADDR, 0x6B, b"\x00")
        sleep_ms(100)
        d = i2c.readfrom_mem(ADDR, 0x3B, 6)
        print("   OK  accel tras despertar -> {}".format(d))
    except Exception as e:
        print("   ERR accel -> {}".format(e))


for f in (100000, 400000):
    intento(f)
    sleep_ms(100)
