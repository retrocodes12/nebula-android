"""Walk a few screens of the debug build on an emulator and screenshot each (CI only, see screens.yml).
Finds controls by their text through uiautomator, so a moved button still gets tapped."""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

MODE = sys.argv[1] if len(sys.argv) > 1 else 'phone'
OUT = 'screens'
os.makedirs(OUT, exist_ok=True)
PKG = 'com.nuvio.ckplayer'
log = open(os.path.join(OUT, 'log.txt'), 'a')


def adb(*a):
    return subprocess.run(['adb'] + list(a), capture_output=True, timeout=120)


def say(m):
    print(m, flush=True); log.write(m + '\n'); log.flush()


def shot(name):
    data = adb('exec-out', 'screencap', '-p').stdout
    open(os.path.join(OUT, '%s-%s.png' % (MODE, name)), 'wb').write(data)
    say('shot %s (%d bytes)' % (name, len(data)))


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
    x = adb('exec-out', 'cat', '/sdcard/ui.xml').stdout
    try:
        return list(ET.fromstring(x).iter('node'))
    except Exception:
        return []


def find(text, exact=False):
    for n in nodes():
        t = (n.get('text') or '') + '|' + (n.get('content-desc') or '')
        parts = [p for p in t.split('|') if p]
        hit = any(p == text for p in parts) if exact else any(text.lower() in p.lower() for p in parts)
        if hit:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(text, exact=False, wait=4):
    xy = find(text, exact)
    if not xy:
        say('not found: ' + text); return False
    adb('shell', 'input', 'tap', str(xy[0]), str(xy[1]))
    time.sleep(wait); return True


def key(k, n=1, wait=0.6):
    for _ in range(n):
        adb('shell', 'input', 'keyevent', k); time.sleep(wait)


apk = [os.path.join(d, f) for d, _, fs in os.walk('app/build/outputs/apk/debug') for f in fs if f.endswith('.apk')][0]
say('install ' + apk + ' ' + adb('install', '-r', '-g', apk).stdout.decode(errors='replace').strip())
adb('shell', 'am', 'start', '-n', PKG + '/.MainActivity')
time.sleep(35)
shot('01-home')

if MODE == 'phone':
    adb('shell', 'input', 'swipe', '540', '1900', '540', '700', '400'); time.sleep(3)
    shot('02-home-rows')
    adb('shell', 'input', 'swipe', '540', '700', '540', '1900', '300'); time.sleep(2)
    if tap('Search', exact=True):
        adb('shell', 'input', 'text', 'the%sbear'); key('KEYCODE_ENTER', wait=8)
        shot('03-search')
        if tap('The Bear', exact=True, wait=10):
            shot('04-title')
            adb('shell', 'input', 'swipe', '540', '1900', '540', '500', '400'); time.sleep(3)
            shot('05-title-episodes')
            tap('System', wait=12)
            shot('06-streams')
    key('KEYCODE_BACK', 4)
    if tap('Library', exact=True): shot('07-library')
    if tap('Settings', exact=True): shot('08-settings')
    if tap('Appearance'): shot('09-appearance'); key('KEYCODE_BACK')
    if tap('Add-ons', exact=True): shot('10-addons')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3'); time.sleep(3)
    if tap('Home', exact=True, wait=6): shot('11-home-large-text')
else:
    key('KEYCODE_DPAD_DOWN'); key('KEYCODE_DPAD_DOWN'); time.sleep(2)
    shot('02-home-rows')
    key('KEYCODE_DPAD_UP', 3); key('KEYCODE_DPAD_CENTER', wait=10)
    shot('03-title')
    key('KEYCODE_DPAD_DOWN', 4); time.sleep(2)
    shot('04-title-lower')
    key('KEYCODE_BACK', wait=3)
    shot('05-back-home')
    key('KEYCODE_DPAD_LEFT', 2); time.sleep(2)
    shot('06-rail')
    key('KEYCODE_DPAD_DOWN', 4); key('KEYCODE_DPAD_CENTER', wait=5)
    shot('07-rail-pick')
say('done')
