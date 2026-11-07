package com.picur.demo.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Escucha el broadcast UDP que envia el Pico W y responde con las IPs
 * locales del PC y el puerto de la API. Asi el dispositivo no depende de
 * una PC_IP fija cuando cambia de red.
 */
@Component
public class DescubrimientoServidor {

    private static final Logger log =
            LoggerFactory.getLogger(DescubrimientoServidor.class);

    private static final String PETICION = "PICUR_DISCOVER_V1";

    private final boolean habilitado;
    private final int puertoDescubrimiento;
    private final int puertoServidor;
    private final AtomicBoolean activo = new AtomicBoolean(false);

    private DatagramSocket socket;
    private Thread hilo;

    public DescubrimientoServidor(
            @Value("${picur.descubrimiento.habilitado:true}") boolean habilitado,
            @Value("${picur.descubrimiento.puerto:5001}") int puertoDescubrimiento,
            @Value("${server.port:8080}") int puertoServidor) {
        this.habilitado = habilitado;
        this.puertoDescubrimiento = puertoDescubrimiento;
        this.puertoServidor = puertoServidor;
    }

    @PostConstruct
    public void iniciar() {
        if (!habilitado) {
            log.info("Descubrimiento UDP deshabilitado");
            return;
        }

        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(puertoDescubrimiento));
            socket.setSoTimeout(1000);
            activo.set(true);

            hilo = new Thread(this::escuchar, "picur-descubrimiento");
            hilo.setDaemon(true);
            hilo.start();

            log.info("Descubrimiento UDP activo en {} (servidor HTTP {})",
                    puertoDescubrimiento, puertoServidor);
        } catch (IOException e) {
            log.error("No se pudo iniciar el descubrimiento UDP en {}: {}",
                    puertoDescubrimiento, e.getMessage(), e);
        }
    }

    private void escuchar() {
        byte[] buffer = new byte[256];

        while (activo.get()) {
            DatagramPacket paquete =
                    new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(paquete);

                String texto = new String(paquete.getData(), 0,
                        paquete.getLength(), StandardCharsets.UTF_8).trim();

                if (!PETICION.equals(texto)) {
                    log.debug("Paquete UDP ignorado desde {}: {}",
                            paquete.getAddress(), texto);
                    continue;
                }

                byte[] respuesta = construirRespuesta()
                        .getBytes(StandardCharsets.UTF_8);

                socket.send(new DatagramPacket(respuesta, respuesta.length,
                        paquete.getAddress(), paquete.getPort()));

                log.debug("Descubrimiento respondido a {}:{}",
                        paquete.getAddress(), paquete.getPort());

            } catch (SocketTimeoutException e) {
                // Normal: el socket tiene timeout para revisar 'activo'.
            } catch (IOException e) {
                if (activo.get()) {
                    log.warn("Error en descubrimiento UDP: {}", e.getMessage());
                }
            }
        }
    }

    private String construirRespuesta() {
        List<String> ips = ipsLocales();
        StringBuilder json = new StringBuilder()
                .append("{\"servicio\":\"picur\",\"version\":1,\"puerto\":")
                .append(puertoServidor)
                .append(",\"ips\":[");

        for (int i = 0; i < ips.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(ips.get(i)).append('"');
        }

        return json.append("]}").toString();
    }

    private List<String> ipsLocales() {
        Set<String> ips = new LinkedHashSet<>();

        try {
            Enumeration<NetworkInterface> redes =
                    NetworkInterface.getNetworkInterfaces();

            if (redes == null) {
                return new ArrayList<>();
            }

            while (redes.hasMoreElements()) {
                NetworkInterface red = redes.nextElement();

                if (!red.isUp() || red.isLoopback() || red.isVirtual()) {
                    continue;
                }

                for (InterfaceAddress direccion : red.getInterfaceAddresses()) {
                    InetAddress ip = direccion.getAddress();

                    if (ip instanceof Inet4Address
                            && !ip.isLoopbackAddress()
                            && !ip.isLinkLocalAddress()) {
                        ips.add(ip.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("No se pudieron listar las IPs locales: {}",
                    e.getMessage());
        }

        return new ArrayList<>(ips);
    }

    @PreDestroy
    public void detener() {
        activo.set(false);

        if (socket != null) {
            socket.close();
        }

        if (hilo != null) {
            try {
                hilo.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("Descubrimiento UDP detenido");
    }
}
