from machine import ADC, Pin


class Piezo:
    """Sensor piezoelectrico en GP26/ADC0 (lectura de 12 bits)."""

    def __init__(self, pin_num=26):
        self.adc = ADC(Pin(pin_num))

    def leer_muestra(self):
        """Una lectura cruda: 16 bits desplazados a 12 bits (0..4095)."""
        return self.adc.read_u16() >> 4
