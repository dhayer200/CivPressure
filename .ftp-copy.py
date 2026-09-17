import ftplib, io, sys

def read_netrc(path):
    host = login = password = None
    with open(path) as f:
        for line in f:
            p = line.split()
            for i in range(0, len(p) - 1, 2):
                k, v = p[i], p[i + 1]
                if k == "machine": host = v
                elif k == "login": login = v
                elif k == "password": password = v
    return host, login, password

host, user, pw = read_netrc(".ftp-netrc")
ftp = ftplib.FTP(host, timeout=120)
ftp.login(user, pw)
ftp.set_pasv(True)

DEST = "/default/NEW_WORLD/datapacks"

def ensure_dir(path):
    try:
        ftp.mkd(path)
        print("  mkdir", path)
    except ftplib.error_perm as e:
        if not str(e).startswith("550"):
            raise

def entries(path):
    out = []
    for name, facts in ftp.mlsd(path):
        if name in (".", ".."):
            continue
        out.append((name, facts.get("type")))
    return out

def copy_file(src, dst):
    buf = io.BytesIO()
    ftp.retrbinary("RETR " + src, buf.write)
    data = buf.getvalue()
    ftp.storbinary("STOR " + dst, io.BytesIO(data))
    print("    file", dst, len(data), "bytes")

def copy_tree(src, dst):
    ensure_dir(dst)
    for name, typ in entries(src):
        s, d = src + "/" + name, dst + "/" + name
        if typ == "dir":
            copy_tree(s, d)
        elif typ == "file":
            copy_file(s, d)

ensure_dir(DEST)

sources = ["/default/datapacks", "/default/world/datapacks"]
for base in sources:
    for name, typ in entries(base):
        s, d = base + "/" + name, DEST + "/" + name
        print("== copy", repr(name), "(", typ, ") from", base)
        if typ == "dir":
            copy_tree(s, d)
        elif typ == "file":
            copy_file(s, d)

ftp.quit()
print("DONE")
