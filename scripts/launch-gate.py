"""The release's launch gate (build.yml): install a release build on an emulator, open it, and play a clear test stream
through the app's own deep link. The app crashing, a verifier error (ART rejecting a class — the debug build's
PlayerScreen already fails that way, and a release that tipped over would crash every install at launch, updater
included), Home never appearing or the player screen never opening fails the release. The picture itself (the pause
board over a playing stream) is reported but does not block: the test stream lives on the network, and a network
hiccup must not hold a release.
With an AV1 file (the API 34 gate), the release also plays it through the app's FFmpeg decoder (nextlib, dav1d): the
emulator has no AV1 chip, so the app hands AV1 to the processor at once (1.85.2). That is the code R8 must not break —
native code calling Java by name — so it blocks: the playback info must say the picture is on the processor, its
rendered frames must grow, the clock must move, and nothing may crash. The file is served from the runner
(10.0.2.2 is the host's loopback, seen from the emulator).
Usage: python3 scripts/launch-gate.py <apk> [<av1 file>]"""
import functools, http.server, os, re, subprocess, sys, threading, time
import xml.etree.ElementTree as ET

PKG = 'com.nuvio.ckplayer'
APK = sys.argv[1]
AV1 = sys.argv[2] if len(sys.argv) > 2 else None
STREAM = 'https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd'


def adb(*a, timeout=120):
    try:
        return subprocess.run(['adb'] + list(a), capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(a, 1, b'', b'')


def sh(*a):
    r = adb('shell', *a); return (r.stdout + r.stderr).decode(errors='replace').strip()


def nodes():
    for flag in ([], ['--compressed'], []):
        adb('shell', 'rm', '-f', '/sdcard/ui.xml'); adb('shell', 'uiautomator', 'dump', *flag, '/sdcard/ui.xml')
        try:
            ns = list(ET.fromstring(adb('exec-out', 'cat', '/sdcard/ui.xml').stdout).iter('node'))
            if ns: return ns
        except Exception:
            pass
        time.sleep(1)
    return []


def texts():
    return ' | '.join((n.get('text') or '') + ' ' + (n.get('content-desc') or '') for n in nodes())


def crashes():
    lines = adb('logcat', '-d', '-v', 'brief', timeout=60).stdout.decode(errors='replace').splitlines()
    if not lines: return ['the log could not be read']
    out = []
    for i, l in enumerate(lines):
        if 'FATAL EXCEPTION' in l and any(('Process: ' + PKG) in m for m in lines[i + 1:i + 4]): out.append(' '.join(lines[i:i + 4]))
        elif 'VerifyError' in l and PKG in l: out.append(l)
        # native code dying (a JNI lookup of a method R8 removed aborts the process; it is no Java exception)
        elif ('>>> ' + PKG + ' <<<') in l or ('JNI DETECTED ERROR' in l) or ('Fatal signal' in l and PKG in l): out.append(l)
    return out


class _Part:
    """the rest of a file from one offset, for a 206 (copyfile reads it to its end)"""
    def __init__(self, f, n): self.f, self.n = f, n

    def read(self, k=-1):
        if self.n <= 0: return b''
        k = self.n if k is None or k < 0 else min(k, self.n)
        b = self.f.read(k); self.n -= len(b); return b

    def close(self): self.f.close()


class _Ranged(http.server.SimpleHTTPRequestHandler):
    """the stdlib's file server, answering one byte range the way a real host does: the player re-opens a file at its
    position after a pause, and without ranges it had to read the whole file again from the start to get there"""
    def send_head(self):
        path = self.translate_path(self.path)
        m = re.match(r'^bytes=(\d+)-(\d*)$', (self.headers.get('Range') or '').strip())
        if not m or not os.path.isfile(path): return super().send_head()
        size = os.path.getsize(path)
        a = int(m.group(1)); b = min(int(m.group(2)) if m.group(2) else size - 1, size - 1)
        if a >= size or b < a: self.send_error(416); return None
        f = open(path, 'rb'); f.seek(a)
        self.send_response(206)
        self.send_header('Content-Type', self.guess_type(path))
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Content-Range', 'bytes %d-%d/%d' % (a, b, size))
        self.send_header('Content-Length', str(b - a + 1))
        self.end_headers()
        return _Part(f, b - a + 1)

    def log_message(self, *a): pass


class _Quiet(http.server.ThreadingHTTPServer):
    daemon_threads = True

    def handle_error(self, request, client_address): pass     # a player closing a connection mid-file is normal


def av1_phase(path):
    """The AV1 file through the release's FFmpeg decoder: [failures]. Logged step by step. Read with the controls held
    still: the remote's Info key opens the playback info (a key first takes the app out of touch mode, as a remote does)
    and each reading is taken paused, so neither fades while uiautomator takes its slow dumps. Four readings, the film
    playing six seconds between them: one may land while the engine re-reads (its picture rows are away then)."""
    fails = []
    d, name = os.path.dirname(os.path.abspath(path)), os.path.basename(path)
    srv = _Quiet(('127.0.0.1', 8765), functools.partial(_Ranged, directory=d))
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    # a fresh start straight into the player (a deep link to a cold app opens the player at once, playing)
    sh('am', 'force-stop', PKG); time.sleep(2)
    sh('am', 'start', '-a', 'android.intent.action.VIEW', '-d', "'nebula://play?mpd=http://10.0.2.2:8765/" + name + "&t=AV1%20Gate'", PKG)
    up = False
    for _ in range(14):
        time.sleep(3)
        if any(n.get('content-desc') == 'Video' for n in nodes()): up = True; break
    print('av1: player', 'open' if up else 'NEVER OPENED', flush=True)
    if not up: return ['AV1: the player screen never opened']
    time.sleep(3)
    adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_UP'); time.sleep(1.5)     # the remote's way in: the controls wake
    adb('shell', 'input', 'keyevent', 'KEYCODE_INFO'); time.sleep(1.5)        # the playback info, on

    def read():
        """paused: (the Decoding line, frames drawn, the elapsed clock in s, what was on screen)"""
        adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE'); time.sleep(2.5)
        t = [((n.get('text') or '') + (n.get('content-desc') or '')).strip() for n in nodes()]
        dec = next((x for x in t if x.startswith('on the processor')), '')
        fr = next((m for m in (re.match(r'^(\d+) of (\d+)$', x) for x in t) if m), None)
        pos = next((m for m in (re.match(r'^(\d+):(\d\d)$', x) for x in t) if m), None)
        drawn = (int(fr.group(2)) - int(fr.group(1))) if fr else -1
        return dec, drawn, (int(pos.group(1)) * 60 + int(pos.group(2))) if pos else -1, [x for x in t if x]

    got = []
    for i in range(4):
        r = read(); got.append(r)
        print('av1: reading %d — decoding %r, frames drawn %d, clock %d s' % (i + 1, r[0], r[1], r[2]), flush=True)
        if i < 3: adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PLAY'); time.sleep(6)
    print('av1: on screen last: ' + ' | '.join(got[-1][3])[:400], flush=True)
    frames = [r[1] for r in got if r[1] > 0]
    clocks = [r[2] for r in got if r[2] >= 0]
    if not any(r[0] for r in got): fails.append('AV1: the picture is not on the processor (no "on the processor" decoding line)')
    if not (len(frames) >= 2 and frames[-1] > frames[0]): fails.append('AV1: frames did not keep being drawn %s' % frames)
    if not (len(clocks) >= 2 and clocks[-1] > clocks[0]): fails.append('AV1: the clock did not move %s' % clocks)
    srv.shutdown()
    return fails


for _ in range(90):
    if sh('getprop', 'sys.boot_completed') == '1': break
    time.sleep(2)
time.sleep(20)
sh('am', 'broadcast', '-a', 'android.intent.action.CLOSE_SYSTEM_DIALOGS')
sh('settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')
inst = adb('install', '-r', '-g', APK).stdout.decode(errors='replace').strip()
print('install:', inst[-200:], flush=True)
fail = []
if 'Success' not in inst: fail.append('install failed: ' + inst[-200:])
adb('logcat', '-c')
sh('monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1')
home = False
for _ in range(12):                       # up to ~60 s for Home (a cold emulator is slow)
    time.sleep(5)
    t = texts()
    if re.search(r'\bHome\b', t) and re.search(r'\bSearch\b', t): home = True; break
print('home:', home, flush=True)
if not home: fail.append('Home never appeared')
sh('am', 'start', '-a', 'android.intent.action.VIEW', '-d', "'nebula://play?mpd=" + STREAM + "&t=Gate'", PKG)
# the player itself (its picture area is named "Video") needs no network, so it blocks: a release whose player screen
# never opens is not shipped. Polled — a cold emulator takes a while to compose it.
video = False
for _ in range(14):                       # up to ~42 s
    time.sleep(3)
    if any(n.get('content-desc') == 'Video' for n in nodes()): video = True; break   # the label exactly, not a title
print('player: screen', 'open' if video else 'NEVER OPENED', flush=True)
if not video: fail.append('the player screen never opened')
# the picture needs the test stream (the network), so the pause board is reported, never blocking — also polled, after
# a tap in the middle wakes the controls the way a viewer would
size = re.search(r'(\d+)x(\d+)', sh('wm', 'size'))
w, h = (int(size.group(1)), int(size.group(2))) if size else (1080, 2400)
time.sleep(8)
adb('shell', 'input', 'tap', str(w // 2), str(h // 2)); time.sleep(1.5)
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE')
played = False
for _ in range(6):                        # up to ~18 s
    time.sleep(3)
    if 'Paused' in texts(): played = True; break
print('player: paused board', 'seen' if played else 'NOT seen (network? — reported, not blocking)', flush=True)
if AV1:
    av1 = av1_phase(AV1)
    print('av1: ' + ('PASSED' if not av1 else 'FAILED: ' + '; '.join(av1)), flush=True)
    fail += av1
bad = crashes()
for b in bad: print('CRASH:', b[:300], flush=True)
fail += bad
print('GATE ' + ('FAILED: ' + '; '.join(fail) if fail else 'PASSED'), flush=True)
sys.exit(1 if fail else 0)
