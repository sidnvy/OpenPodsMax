#!/usr/bin/env python3
"""Check tracked public files only. Reports paths/lines, never matching values."""
import hashlib,pathlib,re,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[1]
files=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode().strip('\0').split('\0')
# Exact, visually reviewed documentation capture; other images remain blocked.
reviewed_images={'app/src/main/res/drawable-nodpi/openpodsmax_icon.png': '99348eafd803bc80c94acb9161ac281f48f55b40042db45657dfbef505e737b7', 'docs/images/pixel-system-controls.png': '8dc7d21d6c1e032c0f2283c590afce3956b9a850924ce375199b2b146ba574c7', 'docs/images/pixel-playback.png': '35e35c7bd26f020b689f1f96b0a49808eab5301a249be2cc8ddf1e8a81510d8d'}
failures=[]
for name in files:
 p=root/name
 if not p.is_file():continue
 if name in reviewed_images:
  if hashlib.sha256(p.read_bytes()).hexdigest()!=reviewed_images[name]:
   failures.append((name,0,'documentation image changed; privacy review required'))
  continue
 if name.startswith(('state/','evidence/','app/src/debug/res/')) or p.suffix.lower() in {'.apk','.aab','.jks','.keystore','.pem','.p12','.pfx','.png','.jpg','.jpeg','.webp','.mp4','.usdz'}:
  failures.append((name,0,'private material or unreviewed binary'))
 try:text=p.read_text()
 except UnicodeDecodeError:continue
 for line,content in enumerate(text.splitlines(),1):
  patterns={'absolute home path':r'/(?:home)/[^/\s]+','private IPv4':r'\b(?:10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+)\b','private key':r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'}
  for kind,pattern in patterns.items():
   if re.search(pattern,content):failures.append((name,line,kind))
for name,line,kind in failures:print(f'{name}:{line}: {kind}')
if failures:sys.exit(1)
print(f'PASS public-file privacy policy ({len(files)} tracked files); run Gitleaks separately for credentials')
