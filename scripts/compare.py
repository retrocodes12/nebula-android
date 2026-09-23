"""Walk the SAME journey in Nebula and in Nuvio (a competitor; its published build, installed and observed only) on
identical emulators, keeping a screenshot + a uiautomator dump per state under paired names (compare.yml). Jobs:
nebula-phone, nuvio-phone, nebula-tv, nuvio-tv. A TV is driven by D-pad keys only. A state an app does not reach is
logged as missing, never faked. Never fails the job: the pictures are the product."""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

JOB = sys.argv[1] if len(sys.argv) > 1 else 'nebula-phone'
APP, FORM = JOB.split('-', 1)
PHONE = FORM == 'phone'
OUT = 'cmp'
os.makedirs(OUT, exist_ok=True)
LOG = open(os.path.join(OUT, 'log.txt'), 'a')
ADDON = 'http://10.0.2.2:8799/manifest.json'
W, H = (1080, 2400) if PHONE else (1920, 1080)
if os.environ.get('CMP_DRY'): time.sleep = lambda s: None
NAVS = ['Home', 'Search', 'Discover', 'Library', 'Settings', 'Profile', 'Account', 'More', 'Downloads', 'Addons']
# labels per app: Nebula's are its own (scripts/screens.py); Nuvio's were learnt from the dumps of earlier runs
L = {
    'nebula': dict(home=['See all'], homenav=['Home'], search=['Search'], library=['Library'], settings=['Settings'],
                   profile=['Profile'], addons=['Add-ons'], add=['Add add-on'], playback=['Playback'],
                   cw=['Continue Watching', 'Continue'], tracks=['Subtitles', 'Audio'], movierow=['Popular · Movies'],
                   account=['Profile']),
    'nuvio': dict(home=['See All', 'See all', 'View all', 'Continue Watching', 'Popular', 'Trending'], homenav=['Home'],
                  search=['Search', 'Discover'], library=['Library', 'My List', 'Watchlist'],
                  settings=['Settings', 'More'], profile=['Profile', 'Account', 'Profiles'],
                  addons=['Addons', 'Add-ons', 'Manage Addons', 'Manage addons', 'Extensions'],
                  add=['Install', 'Add', 'Install Addon', 'Install addon', 'Add Addon', 'Add addon', 'Confirm', 'OK'],
                  playback=['Playback', 'Player', 'Video Player', 'Video'], cw=['Continue Watching', 'Continue watching'],
                  tracks=['Subtitles', 'Subtitle', 'Audio', 'Tracks', 'Audio & Subtitles', 'CC'],
                  movierow=['Popular Movies', 'Popular · Movies', 'Trending Movies', 'Movies'],
                  account=['Account', 'Profile', 'Profiles', 'Sign in', 'Login']),
}[APP]
PAST = ['Skip', 'Skip for now', 'Continue without account', 'Continue as guest', 'Continue as Guest', 'Use without account',
        'Browse as guest', 'Essential', 'Not now', 'Maybe later', 'Later', 'No thanks', 'Get Started', 'Get started', "Let's go",
        'Start', 'Continue', 'Next', 'Done', 'Got it', 'OK', 'Ok', 'Accept', 'I agree', 'Agree', 'Allow', 'Close', 'Dismiss']
EPISODE = ["Failure's Contagious", 'Episode 1', 'E1', 'S1:E1', 'S1 E1', '1. ']


def say(m):
    print(m, flush=True); LOG.write(m + '\n'); LOG.flush()


def adb(*a, timeout=120):
    try:
        return subprocess.run(['adb'] + list(a), capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        say('adb timed out: ' + ' '.join(a[:4])); return subprocess.CompletedProcess(a, 1, b'', b'')


def sh(*a, timeout=120):
    r = adb('shell', *a, timeout=timeout); return (r.stdout + r.stderr).decode(errors='replace').strip()


RAW = [b'']


def nodes():
    """every node of a fresh dump (the full dump sees Compose text; compressed is the fallback), [] when none"""
    for flag in ([], ['--compressed'], []):
        adb('shell', 'rm', '-f', '/sdcard/ui.xml')
        adb('shell', 'uiautomator', 'dump', *flag, '/sdcard/ui.xml', timeout=45)
        x = adb('exec-out', 'cat', '/sdcard/ui.xml').stdout
        try:
            ns = list(ET.fromstring(x).iter('node'))
            if ns: RAW[0] = x; return ns
        except Exception:
            pass
        time.sleep(1)
    RAW[0] = b''; return []


def lab(n):
    return ' / '.join(p for p in ((n.get('text') or ''), (n.get('content-desc') or '')) if p)


def box(n):
    return list(map(int, re.findall(r'-?\d+', n.get('bounds') or '[0,0][0,0]')))


def mid(n):
    b = box(n); return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def texts(ns=None):
    return [lab(n) for n in (nodes() if ns is None else ns) if lab(n)]


def hit(n, t, exact):
    parts = [p for p in ((n.get('text') or ''), (n.get('content-desc') or '')) if p]
    return any(p.lower() == t.lower() for p in parts) if exact else any(t.lower() in p.lower() for p in parts)


def shot(name):
    png = adb('exec-out', 'screencap', '-p').stdout
    open(os.path.join(OUT, name + '.png'), 'wb').write(png)
    ns = nodes()
    open(os.path.join(OUT, name + '.xml'), 'wb').write(RAW[0])
    say('SHOT %s (%d bytes) :: %s' % (name, len(png), ' | '.join(texts(ns))[:1800]))
    return ns


def missing(name, why):
    say('MISSING %s: %s' % (name, why))


def find_any(cands, exact=False, region=None, ns=None, skip_edit=True):
    """(label, centre) of the first candidate on screen (candidates in order of preference), or (None, None)"""
    ns = nodes() if ns is None else ns
    for c in cands:
        for n in ns:
            if skip_edit and n.get('class') == 'android.widget.EditText': continue
            if not hit(n, c, exact): continue
            x, y = mid(n)
            if region and not (region[0] <= y <= region[1]): continue
            if x < 0 or y < 0 or x > W or y > H: continue
            return c, (x, y)
    return None, None


def tap_xy(x, y, wait=4):
    adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(wait)


def tap_any(cands, exact=False, wait=4, region=None, ns=None):
    c, xy = find_any(cands, exact, region, ns)
    if not c: say('not found: %s' % cands); return None
    say('tap %r at %s' % (c, xy)); tap_xy(*xy, wait=wait); return c


def key(k, n=1, wait=0.8):
    """a key press; on a TV the D-pad keys come from a D-pad (a remote), not a keyboard: a text field treats a
    keyboard's arrows as its cursor keys and keeps the focus, a remote's arrows walk out of it"""
    k = k if k.startswith('KEYCODE') else 'KEYCODE_' + k
    src = ['dpad'] if not PHONE and 'DPAD' in k else []
    for _ in range(n):
        adb('shell', 'input', *src, 'keyevent', k); time.sleep(wait)


def on_screen(cands, tries=5, exact=False):
    for _ in range(tries):
        c, _ = find_any(cands, exact)
        if c: return c
        time.sleep(3)
    return None


def swipe_up(wait=3):
    adb('shell', 'input', 'swipe', '540', '1900', '540', '800', '400'); time.sleep(wait)


def swipe_down(wait=2):
    adb('shell', 'input', 'swipe', '540', '800', '540', '1900', '300'); time.sleep(wait)


def edit_field(ns=None):
    for n in (nodes() if ns is None else ns):
        if n.get('class') == 'android.widget.EditText': return n
    return None


def tv_type(t, submit=True):
    """TV: the focus on a text field → OK (a TV field turns writable on OK) → the letters as key presses → Enter"""
    if not seek(['<edit>'], ('UP', 'DOWN', 'RIGHT', 'LEFT'), 5): return False
    key('DPAD_CENTER', wait=1.5); type_text(t); time.sleep(1.5)
    if submit: key('ENTER', wait=3)
    return True


def type_text(t):
    for i in range(0, len(t), 6):
        adb('shell', 'input', 'text', "'" + t[i:i + 6].replace(' ', '%s') + "'"); time.sleep(0.4)


def card_below(y, ns=None, maxdy=900):
    """the leftmost of the top-most card-shaped clickable nodes below y (a poster in a row / a result grid)"""
    cs = []
    for n in (nodes() if ns is None else ns):
        if n.get('clickable') != 'true': continue
        x1, y1, x2, y2 = box(n)
        if y < y1 < y + maxdy and 120 <= x2 - x1 <= 700 and 150 <= y2 - y1 <= 1100 and y2 <= H - 150:
            cs.append((y1 // 40, x1, n))
    cs.sort(key=lambda c: (c[0], c[1]))
    return mid(cs[0][2]) if cs else None


def foreground():
    w = sh('dumpsys window | grep -E "mCurrentFocus|mFocusedApp"')
    if PKG in w: return True
    say('not in front: ' + w.replace('\n', ' ')[:300]); return False


# ---- TV: focus is the cursor -------------------------------------------------------------------------------------
def focus(ns=None):
    """(text of the focused node — its own subtree, else a label just under it (a poster's title) —, node)"""
    ns = nodes() if ns is None else ns
    f = [n for n in ns if n.get('focused') == 'true']
    if not f: return '', None
    n = f[-1]
    t = ' | '.join(lab(m) for m in n.iter('node') if lab(m))
    if not t:
        x1, y1, x2, y2 = box(n)
        t = ' | '.join(lab(m) for m in ns if lab(m) and x1 - 10 <= mid(m)[0] <= x2 + 10 and y1 <= mid(m)[1] <= y2 + 110)
    return t, n


def fmatch(t, n, cands, exact=False):
    if n is None: return None
    edit = n.get('class') == 'android.widget.EditText'
    for c in cands:
        if c == '<edit>':
            if edit: return c
            continue
        if edit: continue
        parts = [p.strip() for p in t.replace(' / ', ' | ').split(' | ')]
        if (any(p.lower() == c.lower() for p in parts) if exact else c.lower() in t.lower()): return c
    return None


def seek(cands, dirs=('DOWN',), steps=8, exact=False, enter=False):
    """move the focus with the D-pad until it sits on a candidate (logged step by step, so a run maps the screen)"""
    for d in dirs:
        last = None
        for i in range(steps + 1):
            t, n = focus()
            c = fmatch(t, n, cands, exact)
            if c:
                say('focus on %r (%s)' % (c, t[:80]))
                if enter: key('DPAD_CENTER', wait=5)
                return c
            say('  focus[%s%d] %s' % (d, i, (t[:90] + ' @' + str(box(n))) if n is not None else '-'))
            if last is not None and t == last[0] and n is not None and box(n) == last[1]: break   # an edge
            last = (t, box(n) if n is not None else None)
            if i < steps: key('DPAD_' + d, wait=0.7)
    say('focus never reached %s' % cands); return None


def tv_nav(cands):
    """a side rail or a top bar: Left to the edge, then walk it up, down, and along"""
    for _ in range(6):
        t, n = focus()
        if fmatch(t, n, cands, True) or (n is not None and box(n)[0] < 120): break
        # a text field keeps the D-pad for its cursor: Back leaves it
        key('BACK' if n is not None and n.get('class') == 'android.widget.EditText' else 'DPAD_LEFT', wait=0.7)
    return seek(cands, ('UP', 'DOWN', 'RIGHT', 'LEFT'), 7, exact=True, enter=True)


# ---- the app -----------------------------------------------------------------------------------------------------
for _ in range(60):
    if sh('getprop', 'sys.boot_completed') == '1': break
    time.sleep(2)
time.sleep(20)
sh('am', 'broadcast', '-a', 'android.intent.action.CLOSE_SYSTEM_DIALOGS')
sh('settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')
sh('settings', 'put', 'system', 'accelerometer_rotation', '0'); sh('settings', 'put', 'system', 'user_rotation', '0')
# typed text arrives as key presses (the on-screen keyboard dumped text into the wrong field — scripts/screens.py)
for ime in sh('ime', 'list', '-s').split():
    say('keyboard off: ' + ime + ' — ' + sh('ime', 'disable', ime))

before = set(sh('pm', 'list', 'packages', '-3').split())
if APP == 'nebula':
    apk = ([os.path.join(d, f) for d, _, fs in os.walk('app/build/outputs/apk/release') for f in fs if f.endswith('.apk')]
           or ['app-release.apk'])[0]
else:
    apk = 'nuvio.apk'
say('install %s: %s' % (apk, adb('install', '-r', '-g', apk, timeout=300).stdout.decode(errors='replace').strip()[-200:]))
new = sorted(set(sh('pm', 'list', 'packages', '-3').split()) - before)
PKG = 'com.nuvio.ckplayer' if APP == 'nebula' else (new[0].split(':', 1)[1] if new else 'com.nuvio.app')
say('package ' + PKG + ' (new: %s)' % new)
comp = ''
for cat in (['android.intent.category.LEANBACK_LAUNCHER'] if not PHONE else []) + ['android.intent.category.LAUNCHER']:
    r = sh('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', cat, PKG).splitlines()
    if r and '/' in r[-1]: comp = r[-1].strip(); break
say('launcher activity ' + comp)
logcat = subprocess.Popen(['adb', 'logcat', '-v', 'time'], stdout=open(os.path.join(OUT, 'logcat.txt'), 'wb'),
                          stderr=subprocess.DEVNULL)


def launch():
    sh('am', 'start', '-n', comp) if comp else sh('monkey', '-p', PKG, '1'); time.sleep(6)


def is_home(ns=None):
    ns = nodes() if ns is None else ns
    if PHONE:
        navs = {c for c in NAVS for n in ns if hit(n, c, True) and mid(n)[1] > H * 0.82}
        if len(navs) >= 2: return True
    return find_any(L['home'], False, ns=ns)[0] is not None


def to_home():
    for i in range(5):
        if not foreground(): say('app not in front: launching'); launch()
        ns = nodes()
        if PHONE and find_any(L['homenav'], True, (H * 0.82, H), ns)[0]:
            tap_any(L['homenav'], True, 4, (H * 0.82, H), ns); return True
        if not PHONE and tv_nav(L['homenav']): return True
        if is_home(ns): return True
        key('BACK', wait=2)
    return False


def nav(which):
    if PHONE:
        ns = nodes()
        return tap_any(L[which], True, 5, (H * 0.82, H), ns) or tap_any(L[which], True, 5, None, ns)
    return tv_nav(L[which])


def enter_on(cands, dirs=('DOWN', 'RIGHT', 'UP'), steps=8, exact=False, wait=5):
    """phone: tap it; TV: walk the focus onto it and press OK"""
    if PHONE: return tap_any(cands, exact, wait)
    return seek(cands, dirs, steps, exact, enter=True)


# 01 first run: whatever the first launch shows, then past it the way a new user would (never signing in)
launch(); time.sleep(20)
ns = shot('01-first-run')
for i in range(8):
    if is_home(ns): say('home reached after %d step(s)' % i); break
    if PHONE:
        c = tap_any(PAST, True, 5, ns=ns)
        if not c and find_any(["Who's watching", 'Choose a profile', 'Select profile', 'Who is watching'], ns=ns)[0]:
            xy = card_below(H // 4, ns); c = xy and 'profile'; xy and tap_xy(*xy, wait=5)
    else:
        c = seek(PAST, ('DOWN', 'RIGHT', 'UP', 'LEFT'), 5, exact=True, enter=True)
        if not c and find_any(["Who's watching", 'Choose a profile', 'Select profile', 'Who is watching'], ns=ns)[0]:
            c = 'profile'; key('DPAD_CENTER', wait=5)
    if not c: say('first run: nothing known to press on'); break
    time.sleep(3); ns = shot('01-first-run-' + 'bcdefghi'[i])
time.sleep(10)

# 02-03 Home
if not is_home(): to_home()
time.sleep(8)
ns = shot('02-home')
if PHONE:
    navs = sorted({(mid(n)[0], lab(n)) for n in ns if lab(n) and mid(n)[1] > H * 0.86 and len(lab(n)) < 24})
    say('NAV (bottom): %s' % navs)
    swipe_up(); swipe_up(1); time.sleep(2)
else:
    key('DPAD_DOWN', 3, wait=1.2); time.sleep(2)
shot('03-home-rows')
if PHONE: swipe_down(); swipe_down()

# 18 add-ons: the runner's stream add-on, installed through the app's own add-on screen
if nav('settings'):
    shot('x-settings-first')
    got = enter_on(L['addons'], exact=True) or (PHONE and (swipe_up() or tap_any(L['addons'], True)))
    if not got and PHONE: shot('x-settings-scrolled')
    if got:
        time.sleep(3); ns = shot('x-addons-before')
        for step in range(4):
            f = edit_field()
            if f is not None: break
            # a button that opens the address field
            c = enter_on(['Add addon', 'Install addon', 'Add Addon', 'Install Addon', 'Add', 'Install', '+', 'Add add-on'],
                         exact=True, wait=4)
            if not c: break
            shot('x-addons-open-%d' % step)
        f = edit_field()
        if f is not None:
            if PHONE: tap_xy(*mid(f), wait=2); type_text(ADDON)
            else: tv_type(ADDON, submit=True)
            time.sleep(2)
            say('add-on field reads: %r' % ((edit_field() or ET.Element('x')).get('text')))
            for step in range(3):
                if on_screen(['Gate Streams'], tries=1): break
                c = enter_on(L['add'], exact=True, wait=6)
                if not c: key('ENTER', wait=6)
                shot('x-addons-after-%d' % step)
            if PHONE: key('BACK', wait=1)   # nothing to put down (no keyboard), but a sheet may be open
        ok = on_screen(['Gate Streams'], tries=3)
        say('the runner\'s add-on is %s' % ('listed' if ok else 'NOT listed'))
        shot('18-addons')
    else:
        missing('18-addons', 'no add-on entry found in settings')
else:
    missing('18-addons', 'settings not found')

# 04-07 Search → the series
to_home()
if nav('search'):
    time.sleep(3); shot('04-search-idle')
    f = edit_field()
    if f is None: say('search: no text field on screen')
    if PHONE:
        if f is not None and f.get('focused') != 'true': tap_xy(*mid(f), wait=3)
        type_text('slow horses')
    else:
        tv_type('slow horses')
    time.sleep(10)
    say('search field reads: %r' % ((edit_field() or ET.Element('x')).get('text')))
    ns = shot('05-search-results')
    if PHONE:
        c, xy = find_any(['Slow Horses'], True, ns=ns)
        if not xy:
            f = edit_field(ns); xy = card_below(box(f)[3] if f is not None else 300, ns)
        opened = bool(xy) and (tap_xy(*xy, wait=10) or True)
    else:
        key('DPAD_DOWN', wait=1)
        opened = seek(['Slow Horses'], ('DOWN', 'RIGHT', 'UP'), 6, enter=True)
        time.sleep(6)
    if opened:
        shot('06-title-series')
        seen = None
        for i in range(4):
            if PHONE:
                seen = find_any(EPISODE, True)[0] or find_any(EPISODE[:2])[0]
                if seen: break
                swipe_up()
            else:
                seen = seek(EPISODE[:2], ('DOWN',), 6)
                break
        shot('07-title-series-episodes')
        # 09-13 an episode's streams → the add-on's row → the player
        ep = enter_on(EPISODE[:2], ('DOWN', 'RIGHT'), 6, wait=14)
        if ep:
            on_screen(['Test stream'], tries=6)
            shot('09-streams')
            if enter_on(['Test stream'], ('DOWN', 'RIGHT', 'UP'), 10, wait=18):
                shot('x-player-first')
                if PHONE: tap_xy(W // 2, H // 2 - 200, wait=1.2)
                else: key('DPAD_CENTER' if APP == 'nebula' else 'DPAD_DOWN', wait=1.2)
                shot('10-player')
                key('MEDIA_PAUSE', wait=6)
                ns = shot('11-player-paused')
                c = find_any(L['tracks'], True, ns=ns)[0]
                if not c:
                    if PHONE: tap_xy(W // 2, H // 2 - 200, wait=1.5)
                    else: key('DPAD_DOWN', wait=1.5)
                if enter_on(L['tracks'], ('DOWN', 'RIGHT', 'LEFT', 'UP'), 8, exact=True, wait=3):
                    shot('12-player-subtitles-audio'); key('BACK', wait=2)
                else:
                    missing('12-player-subtitles-audio', 'no subtitles/audio control found')
                if PHONE:
                    sh('settings', 'put', 'system', 'user_rotation', '1'); time.sleep(4)
                    tap_xy(1200, 540, wait=1.5)
                    shot('13-player-landscape')
                    sh('settings', 'put', 'system', 'user_rotation', '0'); time.sleep(3)
            else:
                missing('10-player', 'no Test stream row to open')
        else:
            missing('09-streams', 'no episode to open')
    else:
        missing('06-title-series', 'no result card')
else:
    missing('04-search-idle', 'search not found')

# 08 a well-known film from Home: the first card of the first movie row
key('BACK', 3, wait=1.5)
to_home(); time.sleep(4)
if PHONE:
    xy = None
    for i in range(3):
        ns = nodes()
        c, hxy = find_any(L['movierow'], ns=ns)
        if hxy: xy = card_below(hxy[1], ns, 700)
        if xy: break
        swipe_up()
    if not xy: xy = card_below(H // 2, nodes())
    if xy: tap_xy(*xy, wait=10)
    shot('08-title-movie') if xy else missing('08-title-movie', 'no movie card')
else:
    key('DPAD_RIGHT', wait=1.5); key('DPAD_DOWN', wait=1.5)
    say('home first row focus: %s' % focus()[0][:120])
    key('DPAD_CENTER', wait=10)
    shot('08-title-movie')

# 14-15 Library / Continue Watching
to_home()
if nav('library'): time.sleep(3); shot('14-library')
else: missing('14-library', 'library not found')
cw = None
to_home()
if not PHONE: key('DPAD_RIGHT', wait=1.5)
for i in range(4):
    c, xy = find_any(L['cw'][:1])
    if c: cw = 'home'; break
    if PHONE: swipe_up()
    else: key('DPAD_DOWN', wait=1.5)
if cw: shot('15-continue-watching')
elif nav('library') and enter_on(L['cw'], ('RIGHT', 'DOWN', 'RIGHT'), 5, exact=True, wait=4): shot('15-continue-watching')
else: missing('15-continue-watching', 'no Continue Watching on Home or in Library')

# 16-17 Settings, 19 Profile
to_home()
if nav('settings'):
    time.sleep(3); shot('16-settings')
    if enter_on(L['playback'], ('DOWN', 'RIGHT'), 10, exact=True, wait=4): shot('17-settings-playback'); key('BACK', wait=2)
    elif PHONE and (swipe_up() or enter_on(L['playback'], exact=True, wait=4)): shot('17-settings-playback'); key('BACK', wait=2)
    else: missing('17-settings-playback', 'no playback entry')
else:
    missing('16-settings', 'settings not found')
to_home()
if nav('profile'): time.sleep(3); shot('19-profile-account')
elif nav('settings') and enter_on(L['account'], ('DOWN', 'RIGHT'), 10, exact=True, wait=4): shot('19-profile-account')
else: missing('19-profile-account', 'no profile/account screen found')

# every other destination the nav offers (for "what else does it have")
to_home()
if PHONE:
    ns = nodes()
    navs = sorted({(mid(n)[0], lab(n)) for n in ns if lab(n) and mid(n)[1] > H * 0.86 and len(lab(n)) < 24})
    for i, (x, t) in enumerate(navs):
        c, xy = find_any([t], True, (H * 0.82, H))
        if xy: tap_xy(*xy, wait=5); shot('x-nav-%d-%s' % (i, re.sub(r'\W+', '_', t)[:20]))
else:
    for _ in range(6): key('DPAD_LEFT', wait=0.6)
    shot('x-nav-open')
    seek(['<never>'], ('UP', 'DOWN'), 8)

logcat.terminate()
say('crashes: %s' % [l for l in open(os.path.join(OUT, 'logcat.txt'), errors='replace') if 'FATAL EXCEPTION' in l][:3])
say('done')
