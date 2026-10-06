from machine import Pin, SoftI2C
from time import sleep_ms

# Combinaciones de pines I2C posibles en el Pico
COMBOS = (
    (0, 1, "GP0/GP1"),
    (4, 5, "GP4/GP5 (config actual)"),
    (8, 9, "GP8/GP9"),
    (2, 3, "GP2/GP3"),
    (6, 7, "GP6/GP7"),
    (10, 11, "GP10/GP11"),
)

for sda, scl, nombre in COMBOS:
    try:
        i2c = SoftI2C(sda=Pin(sda), scl=Pin(scl), freq=100000)
        encontrados = i2c.scan()
        print("{} -> {}".format(nombre, encontrados if encontrados else "vacio"))
    except Exception as e:
        print("{} -> error: {}".format(nombre, e))
    sleep_ms(50)
