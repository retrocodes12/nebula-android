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


_lost = []      # set once the emulator stayed offline through a reconnect: no more waiting after that


def recover():
    """the x86 TV emulator drops off adb now and then ("device offline", every later call failing): reconnect and wait
    up to a minute for it, so a short drop costs one step instead of the rest of the walk"""
    if _lost: return False
    say('adb: the emulator is offline — reconnecting')
    t0 = time.time()
    for _ in range(12):
        try:
            subprocess.run(['adb', 'reconnect', 'offline'], capture_output=True, timeout=30)
            time.sleep(5)
            if subprocess.run(['adb', 'get-state'], capture_output=True, timeout=30).stdout.strip() == b'device':
                say('adb: back after %d s' % (time.time() - t0)); time.sleep(3); return True
        except subprocess.TimeoutExpired:
            pass
    say('adb: still offline after %d s — the emulator is gone for this run' % (time.time() - t0))
    _lost.append(True); return False


def adb(*a, timeout=120, again=True):
    try:
        r = subprocess.run(['adb'] + list(a), capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        say('adb timed out: ' + ' '.join(a[:3]))
        r = subprocess.CompletedProcess(a, 1, b'', b'device offline (timed out)')
    if again and r.returncode != 0 and re.search(rb'device offline|no devices/emulators|device .* not found', r.stderr or b''):
        if recover(): return adb(*a, timeout=timeout, again=False)
    return r


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


def box(n):
    return tuple(map(int, re.findall(r'-?\d+', n.get('bounds') or '[0,0][0,0]')))


def lab(n):
    return ' | '.join(p for p in ((n.get('text') or ''), (n.get('content-desc') or '')) if p)


def focus(ns=None):
    """(the labels of the focused node's own subtree, the node) — ('', None) when nothing is focused"""
    ns = nodes() if ns is None else ns
    f = [n for n in ns if n.get('focused') == 'true']
    if not f: return '', None
    n = f[-1]
    return ' | '.join(lab(m) for m in n.iter('node') if lab(m)), n


def fdesc(t, n):
    return (t[:90] + ' @' + str(box(n))) if n is not None else '-'


def walk_to(pred, k, steps=6):
    """press k until the focused node satisfies pred(text, node); the (text, node), or None — logged step by step"""
    for i in range(steps + 1):
        t, n = focus()
        if n is not None and pred(t, n):
            say('  focus on ' + fdesc(t, n)); return t, n
        say('  focus[%s%d] %s' % (k.replace('KEYCODE_DPAD_', ''), i, fdesc(t, n)))
        if i < steps: key(k, wait=1.0)
    return None


def parts(t):
    return [p.strip() for p in t.split(' | ')]


def ime_shown():
    """True/False from the input-method service's own dump, None when it does not say"""
    out = sh('dumpsys', 'input_method')
    vals = re.findall(r'mInputShown=(true|false)', out)
    return (vals[-1] == 'true') if vals else None


_density = []


def density():
    """px per dp, from the window manager (1080p TV: 2.0, the Pixel 6: 2.625)"""
    if not _density:
        m = re.findall(r'(\d+)', sh('wm', 'density'))
        _density.append((int(m[-1]) / 160.0) if m else 2.0)
    return _density[0]


def board_clear(label, want_next=False, wake=None):
    """the pause board never overlaps the −10 / play / +10 circles (android-phone-6, android-tv-23), by the nodes'
    bounds. The circles carry their labels on the glass itself (GlassCircle), so these are the circles — the play rect
    must be the 80 dp glass, not its 40 dp glyph. want_next: the board must show its Up next pill, the pill that ran
    into the circles, so the check cannot pass on a board that never could have hit them (review R1). wake: brings the
    faded controls back when the transport is not on screen."""
    def read():
        ns = nodes()
        def rects(pred):
            return [(box(n), n.get('text') or '') for n in ns if pred(n.get('text') or '', n.get('content-desc') or '')]
        board = rects(lambda t, d: t == 'Paused' or t.startswith('Ends ') or t.endswith(' min left')
                      or t == 'Under a minute left' or t.startswith('Up next'))
        ctl = rects(lambda t, d: d == 'Play/Pause' or re.match(r'^(Back|Forward) \d+ seconds$', d) is not None)
        play = [box(n) for n in ns if (n.get('content-desc') or '') == 'Play/Pause']
        return board, ctl, play
    board, ctl, play = read()
    if not ctl and wake:
        say(label + ': the controls had faded — waking them'); wake(); time.sleep(1.5)
        board, ctl, play = read()
    say('%s: board %s · transport %s' % (label, board, [c[0] for c in ctl]))
    if MODE == 'phone28' and not (board and ctl):
        say(label + ': API 28 reads no Compose text here — judged by the shot'); return
    expect(label + ': the board and the transport are both on screen', bool(board) and bool(ctl),
           'board %d, transport %d' % (len(board), len(ctl)))
    if want_next:
        expect(label + ': the board shows its Up next pill', any(t.startswith('Up next') for _, t in board),
               ' | '.join(t for _, t in board))
    if play:
        w = (play[0][2] - play[0][0]) / density()
        expect(label + ': the play rect is the glass circle, not its glyph', w >= 70, '%.0f dp wide' % w)
    hit = [(b, c) for b, _ in board for c, _ in ctl if b[0] < c[2] and c[0] < b[2] and b[1] < c[3] and c[1] < b[3]]
    if board and ctl:
        # how much room is left: the nearest gap between any board rect and any circle, sideways or up/down
        gaps = [max(c[0] - b[2], b[0] - c[2], c[1] - b[3], b[1] - c[3]) for b, _ in board for c, _ in ctl]
        say('%s: nearest gap %.0f dp' % (label, min(gaps) / density()))
    expect(label + ': the pause board clears the transport', bool(board) and bool(ctl) and not hit, str(hit[:2]))


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
        key('KEYCODE_ENTER', wait=3)                     # the keyboard's Search: it hides the keyboard now (android-tv-12)
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
                    # the episode has a next one, so the board carries the Up next pill that once ran into the play
                    # circle: checked with the controls up (a tap below the transport wakes them)
                    board_clear('phone paused (series)', want_next=True,
                                wake=lambda: adb('shell', 'input', 'tap', '540', '1450'))
                    shot('06c-series-board-and-controls')
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
    board_clear('phone paused (a film)', wake=lambda: adb('shell', 'input', 'tap', '540', '1450'))
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
        # the streams played above left progress behind: the Continue Watching card with its resume track (1.79.1)
        if tab == 'Library' and tap('Continue Watching', exact=True, must=False):
            shot('10b-library-continue')
            # informational: the walk's clips are short and the deep-link play keeps no title, so there may be no
            # resume point to list
            say('Continue Watching ' + ('lists a card' if on_screen('left', tries=2) else 'is empty (short test clips)'))
    expect('Settings opens', on_screen('Appearance', 'Playback'))
    if tap('Playback'): shot('12-playback'); key('KEYCODE_BACK', wait=2)
    key('KEYCODE_BACK', 2, wait=1.5); front()
    if tap('Profile', exact=True): shot('13-profile')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3'); time.sleep(3)
    key('KEYCODE_BACK', 2, wait=1.5); front()
    if tap('Home', exact=True, wait=6): shot('14-home-large-text')
else:
    # the Featured hero holds still while View Details has focus (android-tv-2): 25 s with no key (a slide lasts 10 s)
    # and the same title must still be on it. THIS is the regression guard for the hold: HeroRotationTest documents the
    # rule but cannot see HeroHeader's LaunchedEffect keys, which are the actual fix
    def hero():
        t = texts()
        i = t.index('View Details') if 'View Details' in t else -1
        return ' | '.join(t[max(0, i - 2):i]) if i > 0 else ''
    f0, h0 = focus()[0], hero()
    time.sleep(25)
    f1, h1 = focus()[0], hero()
    shot('01b-home-held')
    expect('TV Home: the hero holds still under a lit View Details',
           'View Details' in f0 and 'View Details' in f1 and bool(h0) and h0 == h1,
           'before %r, after %r, focus %r / %r' % (h0[:60], h1[:60], f0[:30], f1[:30]))
    # a TV lands on View Details (the hero's button): OK opens that title page
    key('KEYCODE_DPAD_CENTER', wait=10)
    shot('02-title')
    expect('OK on View Details opens a title page', on_screen('Play', tries=4))
    # the focused Play pill grows from its left edge, so the row's clip no longer cuts it (android-tv-14: it read x=26
    # against the page's 32 px gutter)
    t, n = focus()
    say('title page focus: ' + fdesc(t, n))
    expect('title page: the lit Play pill keeps its left edge in the page', n is not None and 'Play' in parts(t) and box(n)[0] >= 30,
           fdesc(t, n))
    key('KEYCODE_DPAD_DOWN', 3); time.sleep(2)
    shot('03-title-lower')
    key('KEYCODE_BACK', wait=4)
    shot('04-back-home')
    expect('Back returns to Home', on_screen('See all', tries=3))
    key('KEYCODE_DPAD_DOWN', 2); time.sleep(2)
    shot('05-home-rows')

    def lands(what, name=None):
        """the page opened on a control of its own, never on its Back circle (android-tv-29: OK there left the page)"""
        time.sleep(1.5)
        t, n = focus()
        expect(what + ' lands on a control of its own, not Back',
               n is not None and 'Back' not in parts(t) and box(n)[1] > 160, fdesc(t, n))
        if name: shot(name)

    # a Catalog (a row's See all) has no landing of its own: it relies on LandingFallback, which stood down when the
    # page's focus seeding lit Back (review R3). The row's See all chip sits above the row's right end
    key('KEYCODE_DPAD_RIGHT', 7, wait=0.7)
    key('KEYCODE_DPAD_UP', wait=1.5)
    t, n = focus()
    say('above the row\'s right end: ' + fdesc(t, n))
    if n is not None and 'See all' in t:
        key('KEYCODE_DPAD_CENTER', wait=6)
        lands('a Catalog (See all)', '05b-catalog')
        key('KEYCODE_BACK', wait=3)
    else:
        failures.append('control not found: See all above a Home row')
    key('KEYCODE_DPAD_LEFT', 10, wait=0.6); time.sleep(1.5)
    shot('06-rail')
    fx = focused_x()
    expect('Left from a row reaches the rail', fx is not None and fx < 260, 'focused x=' + str(fx))

    def on_rail(name):
        return lambda t, n: box(n)[0] < 260 and name in parts(t)

    def row(name):
        return lambda t, n: box(n)[0] > 200 and bool(t) and parts(t)[0] == name

    def page(name, steps=12):
        """from the Settings list: walk Down to the page's row and open it"""
        if not walk_to(row(name), 'KEYCODE_DPAD_DOWN', steps):
            failures.append('control not found: the %s row in Settings' % name); return False
        key('KEYCODE_DPAD_CENTER', wait=4); return True

    # Settings: every page lands on a control of its own (android-tv-29), walked one by one (review R3). Everything
    # shows the Streams and Advanced pages; its chip sits beside Essential, where Settings lands
    if walk_to(on_rail('Settings'), 'KEYCODE_DPAD_DOWN', 4):
        key('KEYCODE_DPAD_CENTER', wait=4)
        if walk_to(lambda t, n: 'Everything' in parts(t), 'KEYCODE_DPAD_RIGHT', 2):
            key('KEYCODE_DPAD_CENTER', wait=2)
        else:
            failures.append('control not found: the Everything chip in Settings')
        for name in ('Appearance', 'Home'):
            if page(name):
                lands('Settings › ' + name, '06a-settings-' + name.lower()); key('KEYCODE_BACK', wait=3)
        if page('Playback'):
            shot('06b-settings-playback')
            lands('Settings › Playback')
            # Up reaches Back, which wears the white ring
            if walk_to(lambda t, n: 'Back' in parts(t), 'KEYCODE_DPAD_UP', 8):
                shot('06c-playback-back-ring')
            else:
                say('Back never took focus on Settings › Playback')
            if walk_to(row('Subtitle style'), 'KEYCODE_DPAD_DOWN', 30):
                key('KEYCODE_DPAD_CENTER', wait=4)
                lands('Settings › Playback › Subtitle style', '06b2-subtitle-style')
                key('KEYCODE_BACK', wait=3)
            else:
                failures.append('control not found: Subtitle style in Settings › Playback')
            key('KEYCODE_BACK', wait=3)
        if page('Streams'):
            lands('Settings › Streams', '06a-settings-streams'); key('KEYCODE_BACK', wait=3)
        # Add-ons: the runner's stream add-on, typed in with the remote (OK makes the field writable)
        if page('Add-ons'):
            lands('Settings › Add-ons')
            if walk_to(lambda t, n: n.get('class') == 'android.widget.EditText', 'KEYCODE_DPAD_UP', 4):
                key('KEYCODE_DPAD_CENTER', wait=2)
                type_text('http://10.0.2.2:8799/manifest.json'); time.sleep(1.5)
                say('add-on field reads: ' + repr(field_text()))
                key('KEYCODE_ENTER', wait=6)
                if not on_screen('Gate Streams', tries=2):
                    tap('Add add-on', exact=True, wait=6, must=False)
                shot('06f-addons')
                expect('TV: the runner\'s stream add-on is added', on_screen('Gate Streams', tries=3))
            else:
                failures.append('control not found: the add-on address field')
            key('KEYCODE_BACK', wait=3)
            if not on_screen('Watch party', tries=1): key('KEYCODE_BACK', wait=3)
        # Friends has no landing of its own either (signed out: its Sign in button): LandingFallback's
        for name in ('Watch party', 'Friends', 'Advanced'):
            if page(name):
                lands('Settings › ' + name, '06a-settings-' + name.split()[0].lower()); key('KEYCODE_BACK', wait=3)
        if walk_to(row('Support Nebula'), 'KEYCODE_DPAD_DOWN', 8):
            key('KEYCODE_DPAD_CENTER', wait=4)
            lands('Settings › Support Nebula', '06a-settings-support'); key('KEYCODE_BACK', wait=3)
        else:
            say('Support Nebula is not listed (it shows once the live service says so) — not walked')

        # Search (android-tv-12): the field → OK types → the query → the keyboard's Search (Enter): the keyboard goes,
        # and ONE Down lands on the FIRST result — with several cards in the row, so the card under the wide field's
        # middle (which plain focus search picks) is another one (review R5)
        key('KEYCODE_DPAD_LEFT', wait=1.5)
        if walk_to(on_rail('Search'), 'KEYCODE_DPAD_UP', 6):
            key('KEYCODE_DPAD_CENTER', wait=5)
            t, n = focus()
            say('search: the remote lands on ' + fdesc(t, n) + (' (the field)' if n is not None and n.get('class') == 'android.widget.EditText' else ''))
            key('KEYCODE_DPAD_CENTER', wait=2)
            say('search: keyboard after OK: ' + str(ime_shown()))
            type_text('the'); time.sleep(2)
            say('search field reads: ' + repr(field_text()))
            key('KEYCODE_ENTER', wait=12)
            ime = ime_shown()
            shot('06d-search-submitted')
            expect('TV search: the keyboard is down after Search', ime is not True, 'shown=' + str(ime))
            fld = [box(m) for m in nodes() if m.get('class') == 'android.widget.EditText']
            key('KEYCODE_DPAD_DOWN', wait=2.5)
            shot('06e-search-down')
            ns = nodes()
            t, n = focus(ns)
            if n is not None and n.get('class') != 'android.widget.EditText':
                fb = box(n); cy = (fb[1] + fb[3]) // 2
                cards = sorted(box(m) for m in ns if m.get('clickable') == 'true' and box(m)[0] > 200 and box(m)[1] < cy < box(m)[3])
                mid = ((fld[0][0] + fld[0][2]) // 2) if fld else None
                say('search: %d cards in the first row %s; the field\'s middle x=%s' % (len(cards), [c[0] for c in cards], mid))
                expect('TV search: several cards in the first row (so a plain Down would pick another)', len(cards) >= 3,
                       '%d cards' % len(cards))
                expect('TV search: one Down lands on the FIRST card of the first row', bool(cards) and fb[0] <= cards[0][0],
                       fdesc(t, n))
            else:
                expect('TV search: one Down lands on the FIRST card of the first row', False, fdesc(t, n))
            # then the series: the field again, the query replaced
            if walk_to(lambda t, n: n.get('class') == 'android.widget.EditText', 'KEYCODE_DPAD_UP', 3):
                key('KEYCODE_DPAD_CENTER', wait=2)
                key('KEYCODE_MOVE_END', wait=0.3); key('KEYCODE_DEL', 6, wait=0.25)
                type_text('slow horses'); time.sleep(2)
                say('search field reads: ' + repr(field_text()))
                key('KEYCODE_ENTER', wait=12)
                key('KEYCODE_DPAD_DOWN', wait=2.5)
                t, n = focus()
                expect('TV search: Down lands on the Slow Horses result', n is not None and 'Slow Horses' in t, fdesc(t, n))
            else:
                failures.append('control not found: the search field, again')
                t = ''
            # a series episode from the runner's add-on: the pause board carries "Up next · S1 E2 · Work Drinks", the
            # pill that ran into the −10 circle (android-tv-23) — checked against the circles with the controls up
            if 'Slow Horses' in t:
                key('KEYCODE_DPAD_CENTER', wait=10)
                if walk_to(lambda t, n: 'Failure' in t, 'KEYCODE_DPAD_DOWN', 16):
                    key('KEYCODE_DPAD_CENTER', wait=14)
                    shot('07a-streams')
                    if walk_to(lambda t, n: 'Long stream' in t, 'KEYCODE_DPAD_DOWN', 6):
                        key('KEYCODE_DPAD_CENTER', wait=30)
                        key('KEYCODE_MEDIA_PAUSE', wait=7)
                        shot('07b-series-paused')
                        expect('TV: the long row plays (its pause board is up)', on_screen('Paused', tries=3))
                        board_clear('TV paused (series)', want_next=True, wake=lambda: key('KEYCODE_DPAD_UP', wait=1))
                        shot('07c-series-board-and-controls')
                        # the long row left a resume point: Continue Watching on Home, its card lit — the art's own
                        # white ring, one ring (review R4/R5)
                        key('KEYCODE_BACK', 2, wait=3)
                        key('KEYCODE_DPAD_LEFT', 5, wait=0.8)
                        if walk_to(on_rail('Home'), 'KEYCODE_DPAD_UP', 6):
                            key('KEYCODE_DPAD_CENTER', wait=8)
                            key('KEYCODE_DPAD_DOWN', wait=2.5)
                            t, n = focus()
                            shot('07d-continue-lit')
                            say('Continue Watching: ' + ('the lit card is ' if 'Slow Horses' in t else 'no Slow Horses card lit — ') + fdesc(t, n))
                        else:
                            say('Home on the rail not reached — no Continue Watching shot')
                    else:
                        failures.append('control not found: the Long stream row')
                else:
                    failures.append('control not found: episode 1 on the title page')
        else:
            failures.append('control not found: Search on the rail')
    else:
        failures.append('control not found: Settings on the rail')
    # the player on a TV, through the deep link: the remote's Pause brings the board
    sh('am', 'start', '-a', 'android.intent.action.VIEW', '-d', "'nebula://play?mpd=" + TEST_STREAM + "&t=Angel%20One'", PKG)
    time.sleep(18)
    key('KEYCODE_DPAD_CENTER', wait=2)
    shot('07-player')
    key('KEYCODE_MEDIA_PAUSE', wait=6)
    shot('08-paused')
    expect('TV paused: the pause board', on_screen('Paused', tries=3))
    board_clear('TV paused (a film)', wake=lambda: key('KEYCODE_DPAD_UP', wait=1))

bad = crashed()
open(os.path.join(OUT, MODE + '-logcat.txt'), 'w').write('\n'.join(
    l for l in adb('logcat', '-d', '-v', 'brief', timeout=60).stdout.decode(errors='replace').splitlines()
    if re.search(r'AndroidRuntime|FATAL|ckplayer|Nebula|System.err', l))[-400000:])
expect('no crash in the log', not bad, (bad[0][:160] if bad else ''))
say('done: %d failed' % len(failures) + (' — ' + '; '.join(failures) if failures else ''))
sys.exit(1 if failures else 0)
