import usocket

class Response:
    def __init__(self, sock):
        self.raw = sock
        self.encoding = "utf-8"
        self._cached = None

    def close(self):
        if self.raw:
            self.raw.close()
            self.raw = None

    @property
    def content(self):
        if self._cached is None:
            self._cached = self.raw.read()
            self.raw.close()
            self.raw = None
        return self._cached

    @property
    def text(self):
        return str(self.content, self.encoding)

    def json(self):
        import ujson
        return ujson.loads(self.content)

def request(method, url, data=None, json=None, headers={}, stream=None):
    try:
        proto, dummy, host, path = url.split("/", 3)
    except ValueError:
        proto, dummy, host = url.split("/", 2)
        path = ""
    if proto == "http:":
        port = 80
    elif proto == "https:":
        import ussl
        port = 443
    else:
        raise ValueError("Unsupported protocol: " + proto)

    if ":" in host:
        host, port = host.split(":", 1)
        port = int(port)

    ai = usocket.getaddrinfo(host, port)
    addr = ai[0][4]
    sock = usocket.socket()
    sock.connect(addr)
    if proto == "https:":
        import ussl
        sock = ussl.wrap_socket(sock, server_hostname=host)

    sock.write(b"%s /%s HTTP/1.0\r\n" % (method, path))
    if not "Host" in headers:
        sock.write(b"Host: %s\r\n" % host)
    for k in headers:
        sock.write(k.encode())
        sock.write(b": ")
        sock.write(headers[k].encode())
        sock.write(b"\r\n")
    if json is not None:
        import ujson
        data = ujson.dumps(json)
        sock.write(b"Content-Type: application/json\r\n")
    if data:
        sock.write(b"Content-Length: %d\r\n" % len(data))
    sock.write(b"\r\n")
    if data:
        sock.write(data if isinstance(data, bytes) else data.encode())

    l = sock.readline()
    partes = l.split(None, 2)
    protover = partes[0]
    status = partes[1]
    msg = partes[2] if len(partes) == 3 else b""
    while True:
        l = sock.readline()
        if not l or l == b"\r\n":
            break

    resp = Response(sock)
    resp.status_code = int(status)
    return resp

def get(url, **kw):
    return request("GET", url, **kw)

def post(url, **kw):
    return request("POST", url, **kw)

def put(url, **kw):
    return request("PUT", url, **kw)

def delete(url, **kw):
    return request("DELETE", url, **kw)