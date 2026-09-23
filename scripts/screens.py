"""Walk the RELEASE build on an emulator, screenshot each screen, and FAIL the job when the app crashes or a screen
that must appear does not (CI only, see screens.yml). Controls are found by their text through uiautomator, so a moved
button still gets tapped. Modes: phone (API 34), phone28 (API 28, the release build must verify on older Android too),
tv (a 1080p Android TV, driven by D-pad only)."""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

MODE = sys.argv[1] if len(sys.argv) > 1 else 'phone'
if MODE.startswith('gate'):
    import glob
    apk0 = sorted(glob.glob('app/build/outputs/apk/release/*.apk'))[0]
    sys.exit(subprocess.run([sys.executable, 'scripts/launch-gate.py', apk0]).returncode)
PHONE = MODE.startswith('phone')
OUT = 'screens'
os.makedirs(OUT, exist_ok=True)
PKG = 'com.nuvio.ckplayer'
log = open(os.path.join(OUT, MODE + '-log.txt'), 'a')
failures = []
# a public, clear test stream (the same one the web rigs play): the player is reached through the app's own deep link
TEST_STREAM = 'https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd'


def adb(*a, timeout=120):
    try:
        return subprocess.run(['adb'] + list(a), capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        say('adb timed out: ' + ' '.join(a[:3]))
        return subprocess.CompletedProcess(a, 1, b'', b'')


def sh(*a):
    r = adb('shell', *a); return (r.stdout + r.stderr).decode(errors='replace').strip()


def say(m):
    print(m, flush=True); log.write(m + '\n'); log.flush()


def shot(name):
    data = adb('exec-out', 'screencap', '-p').stdout
    open(os.path.join(OUT, '%s-%s.png' % (MODE, name)), 'wb').write(data)
    say('shot %s (%d bytes)' % (name, len(data)))


def nodes():
    # uiautomator's full dump crashes on some screens (a null child in its "not accessibility friendly" check), which
    # read as "nothing on screen": the compressed dump skips that check; a failed one is tried again
    for flag in ([], ['--compressed'], []):          # the full dump sees Compose text on every API; compressed is the fallback
        adb('shell', 'rm', '-f', '/sdcard/ui.xml')
        adb('shell', 'uiautomator', 'dump', *flag, '/sdcard/ui.xml')
        x = adb('exec-out', 'cat', '/sdcard/ui.xml').stdout
        try:
            ns = list(ET.fromstring(x).iter('node'))
            if ns: return ns
        except Exception:
            pass
        time.sleep(1)
    return []


def texts():
    out = []
    for n in nodes():
        for k in ('text', 'content-desc'):
            v = n.get(k) or ''
            if v: out.append(v)
    return out


def find(text, exact=False, near=None):
    """the centre of the first node whose text matches; `near`: another text that must be on screen within 400 px below"""
    ns = nodes()
    def hit(n, t, ex):
        parts = [p for p in ((n.get('text') or ''), (n.get('content-desc') or '')) if p]
        return any(p == t for p in parts) if ex else any(t.lower() in p.lower() for p in parts)
    def centre(n):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds'))); return (x1 + x2) // 2, (y1 + y2) // 2
    for n in ns:
        if not hit(n, text, exact): continue
        c = centre(n)
        if near:
            if not any(hit(m, near, True) and 0 <= centre(m)[1] - c[1] < 400 and abs(centre(m)[0] - c[0]) < 400 for m in ns): continue
        return c
    return None


def tap(text, exact=False, wait=4, near=None, must=True):
    xy = find(text, exact, near)
    if not xy:
        say('not found: ' + text)
        if must: failures.append('control not found: ' + text)   # a step that cannot run is a failure, not a skip
        return False
    adb('shell', 'input', 'tap', str(xy[0]), str(xy[1]))
    time.sleep(wait); return True


def key(k, n=1, wait=0.8):
    for _ in range(n):
        adb('shell', 'input', 'keyevent', k); time.sleep(wait)


def expect(label, cond, extra=''):
    say(('PASS ' if cond else 'FAIL ') + label + (' (' + extra + ')' if extra else ''))
    if not cond: failures.append(label)


def on_screen(*want, tries=6):
    """True once every text is on screen (polled: lists arrive from the network)"""
    for _ in range(tries):
        t = ' | '.join(texts())
        if all(w.lower() in t.lower() for w in want): return True
        time.sleep(3)
    return False


def find_class(cls):
    """the centre of the first node of that class (a Compose text field is an android.widget.EditText)"""
    for n in nodes():
        if n.get('class') == cls:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds'))); return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def focused_x():
    """the left edge of the focused node, or None"""
    for n in nodes():
        if n.get('focused') == 'true':
            return int(re.findall(r'\d+', n.get('bounds'))[0])
    return None


def crashed():
    """the app's own crashes only (uiautomator, a system tool, crashes too and is not ours); an unreadable log is a
    failure too — "no crash" must be something the log said, not something it could not say"""
    lines = adb('logcat', '-d', '-v', 'brief', timeout=60).stdout.decode(errors='replace').splitlines()
    if not lines: return ['the log could not be read']
    out = []
    for i, l in enumerate(lines):
        if 'FATAL EXCEPTION' in l and any(('Process: ' + PKG) in m for m in lines[i + 1:i + 4]): out.append(' '.join(lines[i:i + 4]))
        elif 'VerifyError' in l and PKG in l: out.append(l)
    return out


# let the system finish booting and settle, and clear any "isn't responding" dialog a slow boot leaves
for _ in range(60):
    if sh('getprop', 'sys.boot_completed') == '1': break
    time.sleep(2)
time.sleep(25)
sh('am', 'broadcast', '-a', 'android.intent.action.CLOSE_SYSTEM_DIALOGS')
sh('settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')   # the one-time "Viewing full screen" hint
if PHONE:
    # typed text reaches the field as key presses, the way a hardware keyboard's does: with the on-screen keyboard up,
    # `input text` landed one character in the add-on field and the keyboard dumped the rest into the NEXT field that
    # took focus (Search read "slow horseshttp://10.0.2.2:8799/…" — run 35806783092)
    for ime in sh('ime', 'list', '-s').split():
        say('keyboard off: ' + ime + ' — ' + sh('ime', 'disable', ime))


def type_text(t):
    """a few characters at a time (a burst can outrun the field); spaces as %s, the way `input text` wants them"""
    for i in range(0, len(t), 6):
        adb('shell', 'input', 'text', "'" + t[i:i + 6].replace(' ', '%s') + "'"); time.sleep(0.4)


def field_text():
    for n in nodes():
        if n.get('class') == 'android.widget.EditText': return n.get('text') or ''
    return ''
kind = os.environ.get('SCREENS_APK', 'release')
apk = [os.path.join(d, f) for d, _, fs in os.walk('app/build/outputs/apk/' + kind) for f in fs if f.endswith('.apk')][0]
say('install ' + apk + ' ' + adb('install', '-r', '-g', apk).stdout.decode(errors='replace').strip())
adb('logcat', '-c')
cat = 'android.intent.category.LEANBACK_LAUNCHER' if MODE == 'tv' else 'android.intent.category.LAUNCHER'


def front():
    sh('monkey', '-p', PKG, '-c', cat, '1'); time.sleep(6)


say('launch: ' + (sh('monkey', '-p', PKG, '-c', cat, '1').splitlines() or [''])[-1])
time.sleep(40)
sh('am', 'broadcast', '-a', 'android.intent.action.CLOSE_SYSTEM_DIALOGS')
shot('01-home')
expect('Home is up (a catalogue row and the nav)', on_screen('See all', 'Home'), '')

if PHONE:
    # a stream add-on served by the runner (scripts/rig-addon.py): the walk plays a REAL stream row, not only a deep link
    if tap('Settings', exact=True) and tap('Add-ons', exact=True, wait=4):   # exact: the profile card's text says "add-ons" too
        field = find_class('android.widget.EditText')
        if field: adb('shell', 'input', 'tap', str(field[0]), str(field[1])); time.sleep(2)
        else: failures.append('control not found: the add-on address field')
        if field:
            type_text('http://10.0.2.2:8799/manifest.json'); time.sleep(1.5)
            say('add-on field reads: ' + repr(field_text()))
            # the button sits beside the field, above the keyboard (Back here could leave the page); Enter as a fallback
            if not tap('Add add-on', exact=True, wait=6, must=False):
                key('KEYCODE_ENTER', wait=6)
            shot('01b-addons')
            expect('the runner\'s stream add-on is added', on_screen('Gate Streams'))
    key('KEYCODE_BACK', 2, wait=1.5); front()
    adb('shell', 'input', 'swipe', '540', '1900', '540', '700', '400'); time.sleep(3)
    shot('02-home-rows')
    adb('shell', 'input', 'swipe', '540', '700', '540', '1900', '300'); time.sleep(2)
    if tap('Search', exact=True):
        type_text('slow horses'); time.sleep(8)          # no keyboard to put down (switched off above): Back would leave
        say('search field reads: ' + repr(field_text()))
        shot('03-search')
        # the result card, not the typed query: its title with its year beside it
        hit = find('Slow Horses', exact=True, near='2022-') or find('2022-', exact=True)
        if not hit:
            # some API levels hide the card's text from uiautomator: the first card sits under the "RESULT" count
            r = find('RESULT')
            hit = (r[0] if r[0] > 300 else 200, r[1] + 350) if r else None
        if not hit and MODE == 'phone28':
            # API 28's uiautomator reads no Compose text on this screen at all (the result is on the screenshot): the
            # first card's fixed place on a 1080-wide phone; the title page that opens is what the next check proves
            hit = (216, 820); say('search: uiautomator blind here, tapping the first card by position')
        expect('Search finds the series (its result card)', hit is not None)
        if hit:
            adb('shell', 'input', 'tap', str(hit[0]), str(hit[1])); time.sleep(10)
            shot('04-title')
            expect('a series title page with its Play button', on_screen('Play', 'Slow Horses'))
            adb('shell', 'input', 'swipe', '540', '1900', '540', '500', '400'); time.sleep(3)
            shot('05-title-scrolled')
            seen = on_screen('Season 1', tries=2)
            adb('shell', 'input', 'swipe', '540', '1900', '540', '500', '400'); time.sleep(3)
            shot('05b-title-episodes')
            # a shorter phone (API 28's Pixel 3) needs the second swipe before the seasons come up
            expect('the seasons below', seen or on_screen('Season 1', tries=3))
            if tap("Failure's Contagious", wait=14, must=False) or tap('Episode 1', wait=14):
                shot('06-streams')
                expect('the episode\'s streams page lists the add-on\'s row', on_screen('Gate', 'Test stream'))
                if tap('Test stream', wait=16):
                    key('KEYCODE_MEDIA_PAUSE', wait=6)
                    shot('06b-played-from-row')
                    expect('a stream row plays (paused board over it)', on_screen('Paused', tries=3))
                    key('KEYCODE_BACK', 3, wait=1.5)
    # the player, through the app's own deep link, with a clear test stream
    key('KEYCODE_BACK', 3, wait=1.5)
    sh('am', 'start', '-a', 'android.intent.action.VIEW', '-d', "'nebula://play?mpd=" + TEST_STREAM + "&t=Angel%20One'", PKG)
    time.sleep(18)
    adb('shell', 'input', 'tap', '540', '1200'); time.sleep(1.5)
    shot('07-player')
    key('KEYCODE_MEDIA_PAUSE', wait=6)
    shot('08-paused')
    expect('paused: the pause board names what is playing', on_screen('Paused', 'Angel One', tries=3))
    # on its side: the controls step aside after a moment and the board shows (1.79 fix)
    sh('settings', 'put', 'system', 'accelerometer_rotation', '0'); sh('settings', 'put', 'system', 'user_rotation', '1')
    time.sleep(3); adb('shell', 'input', 'tap', '1200', '540'); time.sleep(8)
    shot('09-paused-landscape')
    expect('landscape, paused: the pause board shows', on_screen('Paused', tries=3))
    sh('settings', 'put', 'system', 'user_rotation', '0'); time.sleep(3)
    key('KEYCODE_BACK', 3, wait=1.5)
    for tab, name in (('Library', '10-library'), ('Settings', '11-settings')):
        key('KEYCODE_BACK', 2, wait=1.5); front()
        if tap(tab, exact=True): shot(name)
    expect('Settings opens', on_screen('Appearance', 'Playback'))
    if tap('Playback'): shot('12-playback'); key('KEYCODE_BACK', wait=2)
    key('KEYCODE_BACK', 2, wait=1.5); front()
    if tap('Profile', exact=True): shot('13-profile')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3'); time.sleep(3)
    key('KEYCODE_BACK', 2, wait=1.5); front()
    if tap('Home', exact=True, wait=6): shot('14-home-large-text')
else:
    # a TV lands on View Details (the hero's button): OK opens that title page
    key('KEYCODE_DPAD_CENTER', wait=10)
    shot('02-title')
    expect('OK on View Details opens a title page', on_screen('Play', tries=4))
    key('KEYCODE_DPAD_DOWN', 3); time.sleep(2)
    shot('03-title-lower')
    key('KEYCODE_BACK', wait=4)
    shot('04-back-home')
    expect('Back returns to Home', on_screen('See all', tries=3))
    key('KEYCODE_DPAD_DOWN', 2); time.sleep(2)
    shot('05-home-rows')
    key('KEYCODE_DPAD_LEFT', 6); time.sleep(2)
    shot('06-rail')
    fx = focused_x()
    expect('Left from a row reaches the rail', fx is not None and fx < 260, 'focused x=' + str(fx))
    # the player on a TV, through the deep link: the remote's Pause brings the board
    sh('am', 'start', '-a', 'android.intent.action.VIEW', '-d', "'nebula://play?mpd=" + TEST_STREAM + "&t=Angel%20One'", PKG)
    time.sleep(18)
    key('KEYCODE_DPAD_CENTER', wait=2)
    shot('07-player')
    key('KEYCODE_MEDIA_PAUSE', wait=6)
    shot('08-paused')
    expect('TV paused: the pause board', on_screen('Paused', tries=3))

bad = crashed()
open(os.path.join(OUT, MODE + '-logcat.txt'), 'w').write('\n'.join(
    l for l in adb('logcat', '-d', '-v', 'brief', timeout=60).stdout.decode(errors='replace').splitlines()
    if re.search(r'AndroidRuntime|FATAL|ckplayer|Nebula|System.err', l))[-400000:])
expect('no crash in the log', not bad, (bad[0][:160] if bad else ''))
say('done: %d failed' % len(failures) + (' — ' + '; '.join(failures) if failures else ''))
sys.exit(1 if failures else 0)
