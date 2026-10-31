#!/usr/bin/env python3
"""Builds app/src/main/assets/teams.json from a clone of Bayti TV (https://github.com/HMubaireek/bayti-tv):
its leagues (Prefs.LEAGUES), its club list (assets/shared/teams.json) and its Arabic names (assets/shared/teams-ar.json).

    python3 tools/teams.py /path/to/bayti-tv
"""
import json, os, re, sys

tv = sys.argv[1]
prefs = open(os.path.join(tv, 'android/app/src/main/java/sa/bayti/screensaver/Prefs.java'), encoding='utf-8').read()
block = prefs[prefs.index('static final String[][] LEAGUES'):]
block = block[:block.index('};')]
leagues = [[en, ar] for en, ar in re.findall(r'\{"([^"]+)",\s*"([^"]+)"\}', block)]
leagues.append(['National teams', 'المنتخبات'])

teams = []
for t in json.load(open(os.path.join(tv, 'assets/shared/teams.json'), encoding='utf-8')):
    row = {'id': t['id'], 'en': t['en'], 'ar': t.get('ar', ''), 'l': t.get('leagues', [])}
    if t.get('alt'): row['alt'] = t['alt']
    if t.get('arFrom') == 'translit': row['tr'] = 1
    teams.append(row)

names = json.load(open(os.path.join(tv, 'assets/shared/teams-ar.json'), encoding='utf-8'))
out = {'leagues': leagues, 'teams': teams, 'names': names}
dst = os.path.join(os.path.dirname(__file__), '..', 'app/src/main/assets/teams.json')
with open(dst, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
print(len(leagues), 'leagues,', len(teams), 'teams,', len(names), 'names ->', os.path.normpath(dst))
