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


def find_any(cands, exact=False, region=None, ns=None, skip_edit=True, maxlen=999):
    """(label, centre) of the first candidate on screen (candidates in order of preference), or (None, None)"""
    ns = nodes() if ns is None else ns
    for c in cands:
        for n in ns:
            if skip_edit and n.get('class') == 'android.widget.EditText': continue
            if not hit(n, c, exact) or len(lab(n)) > maxlen: continue
            x, y = mid(n)
            if region and not (region[0] <= y <= region[1]): continue
            if x < 0 or y < 0 or x > W or y > H: continue
            return c, (x, y)
    return None, None


def tap_xy(x, y, wait=4):
    adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(wait)


def tap_any(cands, exact=False, wait=4, region=None, ns=None, maxlen=999):
    c, xy = find_any(cands, exact, region, ns, maxlen=maxlen)
    if not c: say('not found: %s' % cands); return None
    say('tap %r at %s' % (c, xy)); tap_xy(*xy, wait=wait); return c


def key(k, n=1, wait=0.8):
    """a key press; on a TV the D-pad keys come from a D-pad (a remote), not a keyboard: a text field treats a
    keyboard's arrows as its cursor keys and keeps the focus, a remote's arrows walk out of it"""
    k = k if k.startswith('KEYCODE') else 'KEYCODE_' + k
    src = ['dpad'] if not PHONE and 'DPAD' in k else []
    for _ in range(n):
        adb('shell', 'input', *src, 'keyevent', k); time.sleep(wait)


def hw_dpad(d):
    """a D-pad press through the emulator's own key device (a real input device, as a remote is): Compose's text
    field moves the focus only for a real D-pad — `input keyevent` comes from a virtual device, which it treats as a
    keyboard's cursor keys, so the focus could never leave a field that way (run 35826723205)"""
    code = {'UP': 'KEY_UP', 'DOWN': 'KEY_DOWN', 'LEFT': 'KEY_LEFT', 'RIGHT': 'KEY_RIGHT'}[d]
    r = adb('emu', 'event', 'send', code.join(['EV_KEY:', ':1']), code.join(['EV_KEY:', ':0']), timeout=20)
    say('  hardware %s: %s' % (d, (r.stdout + r.stderr).decode(errors='replace').strip()[:80])); time.sleep(0.8)


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
    key('DPAD_CENTER', wait=1.5); type_text(t); time.sleep(1.5); tidy_field(t)
    got = (edit_field() or ET.Element('x')).get('text') or ''
    if got.strip().lower() != t.lower():
        say('field reads %r: clearing and typing again' % got)
        key('KEYCODE_MOVE_END', wait=0.3); key('KEYCODE_DEL', len(got) + 2, wait=0.15); type_text(t); time.sleep(1.5)
    if submit: key('ENTER', wait=3)
    return True


def tidy_field(t):
    """a field that reads 'x' + t: the stray characters before t are deleted"""
    f = edit_field(); v = (f.get('text') or '') if f is not None else ''
    if v != t and v.endswith(t) and len(v) - len(t) < 4:
        key('MOVE_HOME', wait=0.3); key('FORWARD_DEL', len(v) - len(t), wait=0.3)
        say('field tidied: %r -> %r' % (v, (edit_field() or ET.Element('x')).get('text')))


def screen():
    for n in nodes():
        b = box(n)
        if b[2] > 0 and b[3] > 0: return b[2], b[3]
    return W, H


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
            if last is not None and t == last[0] and n is not None and box(n) == last[1]:
                # adb's injected arrows come from a virtual full keyboard, which a Compose text field keeps as
                # cursor keys (a remote's D-pad is non-alphabetic and walks out): Tab moves the focus on instead
                if n.get('class') == 'android.widget.EditText' and not last[2]:
                    say('  (in a text field: Tab)'); key('TAB', wait=1); last = (t, box(n), True); continue
                break   # an edge
            last = (t, box(n) if n is not None else None, bool(last and last[2]))
            if i < steps:
                if n is not None and n.get('class') == 'android.widget.EditText' and '<edit>' not in cands: hw_dpad(d)
                else: key('DPAD_' + d, wait=0.7)
    say('focus never reached %s' % cands); return None


def tv_nav(cands):
    """a side rail or a top bar: Left to the edge, then walk it up, down, and along"""
    for _ in range(6):
        t, n = focus()
        if fmatch(t, n, cands, True) or (n is not None and box(n)[0] < 120): break
        if n is not None and n.get('class') == 'android.widget.EditText': hw_dpad('LEFT')
        else: key('DPAD_LEFT', wait=0.7)
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


T0 = time.time()
PICKER = ["Who's watching", 'Choose a profile', 'Select profile', 'Who is watching']
ADDONISH = ['Addons', 'Add-ons', 'Manage Addons', 'Manage addons', 'Manage add-ons', 'Extensions', 'Addon', 'Add-on']


def phone_scroll_tap(cands, exact=True, swipes=4, wait=5, maxlen=40):
    """phone: tap the first candidate, scrolling the page down to find it"""
    for i in range(swipes + 1):
        c = tap_any(cands, exact, wait, maxlen=maxlen)
        if c: return c
        if i < swipes: swipe_up()
    return None


def tv_settings(cands, exact=True, enter=True):
    """TV settings: from the pane back to the category list (a left column), then walk it"""
    t, n = focus()
    if n is not None and box(n)[0] > 660: key('DPAD_LEFT', wait=1)
    return seek(cands, ('UP', 'DOWN'), 11, exact, enter)


def profile_picker(ns):
    """Nuvio's "Who's watching?" (a fresh local account showed no profile at all): the first tile, else make one"""
    time.sleep(8); ns = nodes()   # the tiles may come a moment after the title
    heads = [n for n in ns if any(hit(n, p, False) for p in PICKER)]
    xy = card_below(box(heads[0])[3] if heads else H // 4, ns, 700)
    if xy and not find_any(['Manage Profiles'], True, ns=[n for n in ns if mid(n) == xy])[0]:
        tap_xy(*xy, wait=5); return 'profile tile'
    if not tap_any(['Manage Profiles', 'Manage profiles'], True, 5): return None
    shot('01-first-run-profiles-manage')
    if not tap_any(['Add Profile', 'Add profile', 'Create Profile', 'Create profile', 'New Profile', 'New profile', 'Add'], True, 5) \
            and not tap_any(['add profile', 'create', 'new profile'], False, 5, maxlen=30):
        return None
    shot('01-first-run-profiles-add')
    f = edit_field()
    if f is not None: tap_xy(*mid(f), wait=2); type_text('Guest'); time.sleep(1)
    tap_any(['Save', 'Create', 'Done', 'Continue', 'OK', 'Add', 'Confirm'], True, 5)
    ns = shot('01-first-run-profiles-saved')
    for _ in range(3):
        if find_any(PICKER, ns=ns)[0]: break
        if find_any(['Guest'], True, ns=ns)[0] and not find_any(['Manage Profiles'], True, ns=ns)[0]: break
        key('BACK', wait=3); ns = nodes()
    return tap_any(['Guest'], True, 6) or 'profile made'


# 01 first run: whatever the first launch shows, then past it the way a new user would (never signing in)
launch(); time.sleep(20)
ns = shot('01-first-run')
for i in range(8):
    if is_home(ns): say('home reached after %d step(s)' % i); break
    c = None
    if PHONE:
        if find_any(PICKER, ns=ns)[0]: c = profile_picker(ns)
        else: c = tap_any(PAST, True, 5, ns=ns, maxlen=40)
    else:
        if find_any(PICKER, ns=ns)[0]: c = 'profile'; key('DPAD_CENTER', wait=5)
        else: c = seek(PAST, ('DOWN', 'RIGHT', 'UP', 'LEFT'), 5, exact=True, enter=True)
    if not c: say('first run: nothing known to press on'); break
    time.sleep(4); ns = shot('01-first-run-' + 'bcdefghi'[i])
time.sleep(8)

# 02-03 Home
if not is_home(): to_home()
time.sleep(6)
ns = shot('02-home')
if PHONE:
    navs = sorted({(mid(n)[0], lab(n)) for n in ns if lab(n) and mid(n)[1] > H * 0.86 and len(lab(n)) < 24})
    say('NAV (bottom): %s' % navs)
    swipe_up(); swipe_up(1); time.sleep(2)
else:
    key('DPAD_RIGHT', wait=1); key('DPAD_DOWN', 3, wait=1.2); time.sleep(2)
shot('03-home-rows')
if PHONE: swipe_down(); swipe_down()


# 18 add-ons: the runner's stream add-on, installed through the app's own add-on screen
def open_addons():
    if not nav('settings'): return None
    shot('x-settings-first')
    if PHONE:
        return phone_scroll_tap(ADDONISH[:6], True, 4) or phone_scroll_tap(ADDONISH, False, 0, maxlen=24)
    if tv_settings(L['addons']): return 'row'
    # a category list with panes (Nuvio TV): the category whose pane offers add-ons
    for cat in ['Content & Discovery', 'Integrations', 'Advanced', 'Layout', 'Playback', 'Profiles', 'Appearance']:
        if not tv_settings([cat], enter=False): continue
        ns = shot('x-settings-' + re.sub(r'\W+', '_', cat))
        if find_any(ADDONISH, False, ns=ns, maxlen=30)[0]:
            key('DPAD_RIGHT', wait=1.5)
            if seek(ADDONISH, ('DOWN', 'UP'), 10, enter=True): return cat
    return None


if open_addons():
    time.sleep(3); shot('x-addons-before')
    for step in range(3):
        if edit_field() is not None: break
        c = enter_on(['Add addon', 'Install addon', 'Add Addon', 'Install Addon', 'Add add-on', 'Add', 'Install', '+',
                      'Add from URL', 'Install from URL'], ('DOWN', 'RIGHT', 'UP'), 8, exact=True, wait=4)
        if not c: break
        shot('x-addons-open-%d' % step)
    f = edit_field()
    if f is not None:
        if PHONE: tap_xy(*mid(f), wait=2); type_text(ADDON); tidy_field(ADDON)
        else: tv_type(ADDON, submit=True)
        time.sleep(3)
        say('add-on field reads: %r' % ((edit_field() or ET.Element('x')).get('text')))
        for step in range(3):
            if on_screen(['Gate Streams'], tries=1): break
            c = enter_on(L['add'], ('DOWN', 'RIGHT', 'UP'), 6, exact=True, wait=6)
            if not c and step == 0: key('ENTER', wait=6)
            shot('x-addons-after-%d' % step)
    else:
        say('add-ons: no address field reached')
    ok = on_screen(['Gate Streams'], tries=3)
    say('the runner\'s add-on is %s' % ('listed' if ok else 'NOT listed'))
    shot('18-addons')
else:
    missing('18-addons', 'no add-on screen found from settings')

# 04-07 Search → the series
key('BACK', 2, wait=1.5)
to_home()
if nav('search'):
    time.sleep(3); shot('04-search-idle')
    f = edit_field()
    if f is None: say('search: no text field on screen')
    if PHONE:
        if f is not None and f.get('focused') != 'true': tap_xy(*mid(f), wait=3)
        type_text('slow horses'); tidy_field('slow horses'); key('ENTER', wait=1)
    else:
        tv_type('slow horses')
    time.sleep(10)
    say('search field reads: %r' % ((edit_field() or ET.Element('x')).get('text')))
    ns = shot('05-search-results')
    if PHONE:
        xy = None
        for i in range(4):
            c, xy = find_any(['Slow Horses'], True, ns=ns)
            if xy: break
            swipe_up(); ns = nodes()
        opened = bool(xy) and (tap_xy(*xy, wait=12) or True)
    else:
        opened = seek(['Slow Horses'], ('DOWN', 'RIGHT', 'DOWN', 'UP'), 8, exact=True, enter=True)
        time.sleep(8)
    if opened:
        shot('06-title-series')
        if PHONE:
            for i in range(5):
                if find_any(EPISODE[:1], True)[0] or find_any(EPISODE[1:2])[0]: break
                swipe_up()
        else:
            seek(EPISODE[:2], ('DOWN',), 12)
        shot('07-title-series-episodes')
        # 09-13 an episode's streams → the add-on's row → the player
        ep = tap_any(EPISODE[:1], True, 14) or tap_any(EPISODE[1:2], False, 14) if PHONE else \
            seek(EPISODE[:2], ('DOWN', 'RIGHT'), 6, enter=True)
        if ep:
            time.sleep(6 if PHONE else 10)
            on_screen(['Test stream'], tries=6)
            shot('09-streams')
            got = phone_scroll_tap(['Test stream'], False, 2, 18) if PHONE else \
                seek(['Test stream'], ('DOWN', 'RIGHT', 'UP', 'LEFT'), 10, enter=True)
            if got:
                time.sleep(4 if PHONE else 10)
                shot('x-player-first')
                sw, shh = screen()
                if PHONE: tap_xy(sw // 2, shh // 2 - (200 if shh > sw else 0), wait=1.2)
                else: key('DPAD_CENTER' if APP == 'nebula' else 'DPAD_DOWN', wait=1.2)
                shot('10-player')
                key('MEDIA_PAUSE', wait=6)
                ns = shot('11-player-paused')
                if not find_any(L['tracks'], True, ns=ns)[0]:
                    if PHONE: tap_xy(sw // 2, shh // 2 - (200 if shh > sw else 0), wait=1.5)
                    else: key('DPAD_DOWN', wait=1.5)
                if PHONE: c = tap_any(L['tracks'], True, 3) or tap_any(['subtitle', 'audio', 'track'], False, 3, maxlen=30)
                else: c = seek(L['tracks'], ('DOWN', 'RIGHT', 'LEFT', 'UP'), 8, exact=True, enter=True)
                if c: time.sleep(2); shot('12-player-subtitles-audio'); key('BACK', wait=2)
                else: missing('12-player-subtitles-audio', 'no subtitles/audio control found')
                if PHONE:
                    sh('settings', 'put', 'system', 'user_rotation', '1'); time.sleep(4)
                    tap_xy(1200, 540, wait=1.5)
                    shot('13-player-landscape')
                    sh('settings', 'put', 'system', 'user_rotation', '0'); time.sleep(3)
                key('BACK', 2, wait=2)
            else:
                missing('10-player', 'no Test stream row to open')
        else:
            missing('09-streams', 'no episode to open')
    else:
        missing('06-title-series', 'no result card')
else:
    missing('04-search-idle', 'search not found')

# 08 a well-known film from Home: The End of Oak Street (the first of Cinemeta's popular movies today), else the first
# card of the first movie row
key('BACK', 3, wait=1.5)
to_home(); time.sleep(4)
FILM = ['The End of Oak Street']
if PHONE:
    xy = None
    for i in range(3):
        ns = nodes()
        c, xy = find_any(FILM, True, ns=ns)
        if xy and xy[1] < 600: xy = None   # the hero's title is not a card
        if not xy:
            c, hxy = find_any(L['movierow'], ns=ns)
            if hxy: xy = card_below(hxy[1], ns, 700)
        if xy: break
        swipe_up()
    if xy: tap_xy(*xy, wait=10); shot('08-title-movie')
    else: missing('08-title-movie', 'no movie card')
else:
    key('DPAD_RIGHT', wait=1.5)
    if seek(FILM, ('DOWN', 'RIGHT'), 7, enter=True): time.sleep(6); shot('08-title-movie')
    else:
        say('home: the film not reached, taking the focused card'); key('DPAD_CENTER', wait=10); shot('08-title-movie')

# 14-15 Library / Continue Watching
key('BACK', 2, wait=1.5)
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
    got = phone_scroll_tap(L['playback'], True, 3) if PHONE else tv_settings(L['playback'])
    if got: time.sleep(2); shot('17-settings-playback'); key('BACK', wait=2)
    else: missing('17-settings-playback', 'no playback entry')
else:
    missing('16-settings', 'settings not found')
to_home()
if nav('profile'): time.sleep(3); shot('19-profile-account')
elif nav('settings') and (phone_scroll_tap(L['account'], True, 3) if PHONE else tv_settings(L['account'], enter=False)):
    time.sleep(2); shot('19-profile-account')
else: missing('19-profile-account', 'no profile/account screen found')

# what else each app has: every nav destination (phone), every settings category (TV)
to_home()
if PHONE:
    ns = nodes()
    navs = sorted({(mid(n)[0], lab(n)) for n in ns if lab(n) and mid(n)[1] > H * 0.86 and len(lab(n)) < 24})
    for i, (x, t) in enumerate(navs):
        c, xy = find_any([t], True, (H * 0.82, H))
        if xy: tap_xy(*xy, wait=5); shot('x-nav-%d-%s' % (i, re.sub(r'\W+', '_', t)[:20]))
elif APP == 'nuvio' and nav('settings'):
    for cat in ['Profiles', 'Appearance', 'Layout', 'Content & Discovery', 'Integrations', 'Tracking', 'About', 'Advanced']:
        if tv_settings([cat], enter=False): shot('x-settings-' + re.sub(r'\W+', '_', cat))

logcat.terminate()
say('crashes: %s' % [l for l in open(os.path.join(OUT, 'logcat.txt'), errors='replace') if 'FATAL EXCEPTION' in l][:3])
say('done in %d s' % (time.time() - T0))
