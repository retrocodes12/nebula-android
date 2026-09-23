"""The release's launch gate (build.yml): install a release build on an emulator, open it, and play a clear test stream
through the app's own deep link. The app crashing, a verifier error (ART rejecting a class — the debug build's
PlayerScreen already fails that way, and a release that tipped over would crash every install at launch, updater
included) or Home never appearing fails the release. The player step is reported but only a crash there blocks: the
test stream lives on the network, and a network hiccup must not hold a release.
Usage: python3 scripts/launch-gate.py <apk>"""
import re, subprocess, sys, time
import xml.etree.ElementTree as ET

PKG = 'com.nuvio.ckplayer'
APK = sys.argv[1]
STREAM = 'https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd'


def adb(*a, timeout=120):
    try:
        return subprocess.run(['adb'] + list(a), capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(a, 1, b'', b'')


def sh(*a):
    r = adb('shell', *a); return (r.stdout + r.stderr).decode(errors='replace').strip()


def texts():
    for flag in ([], ['--compressed'], []):
        adb('shell', 'rm', '-f', '/sdcard/ui.xml'); adb('shell', 'uiautomator', 'dump', *flag, '/sdcard/ui.xml')
        try:
            ns = list(ET.fromstring(adb('exec-out', 'cat', '/sdcard/ui.xml').stdout).iter('node'))
            if ns: return ' | '.join((n.get('text') or '') + ' ' + (n.get('content-desc') or '') for n in ns)
        except Exception:
            pass
        time.sleep(1)
    return ''


def crashes():
    lines = adb('logcat', '-d', '-v', 'brief', timeout=60).stdout.decode(errors='replace').splitlines()
    if not lines: return ['the log could not be read']
    out = []
    for i, l in enumerate(lines):
        if 'FATAL EXCEPTION' in l and any(('Process: ' + PKG) in m for m in lines[i + 1:i + 4]): out.append(' '.join(lines[i:i + 4]))
        elif 'VerifyError' in l and PKG in l: out.append(l)
    return out


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
time.sleep(20)
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE'); time.sleep(6)
played = 'Paused' in texts()
print('player: paused board', 'seen' if played else 'NOT seen (network? — reported, not blocking)', flush=True)
bad = crashes()
for b in bad: print('CRASH:', b[:300], flush=True)
fail += bad
print('GATE ' + ('FAILED: ' + '; '.join(fail) if fail else 'PASSED'), flush=True)
sys.exit(1 if fail else 0)
