#!/usr/bin/env python3
"""Posts the tail of build.log as a GitHub issue so CI failures are readable via API."""
import json
import os
import pathlib
import urllib.request

log = pathlib.Path('build.log')
text = log.read_text(errors='replace') if log.exists() else 'no build.log'
interesting = [line for line in text.splitlines()
               if 'error:' in line or 'FAILED' in line or 'What went wrong' in line
               or line.startswith('> Task')]
body = '\n'.join(interesting[-300:]) or text[-6000:]
body = '```\n' + body[-12000:] + '\n```'
sha = os.environ.get('SHA', 'unknown')[:7]
payload = json.dumps({'title': 'build failure ' + sha, 'body': body}).encode()
request = urllib.request.Request(
    'https://api.github.com/repos/' + os.environ['REPO'] + '/issues',
    data=payload, method='POST',
    headers={'Authorization': 'token ' + os.environ['GH_TOKEN'],
             'Accept': 'application/vnd.github+json',
             'Content-Type': 'application/json'})
with urllib.request.urlopen(request) as response:
    print('reported', response.status)
