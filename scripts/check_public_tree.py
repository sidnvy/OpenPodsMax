#!/usr/bin/env python3
"""Check tracked public files only. Reports paths/lines, never matching values."""
import hashlib,pathlib,re,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[1]
files=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode().strip('\0').split('\0')
# Exact reviewed images: app illustration, owner-approved product images and documentation. Other binaries remain blocked.
reviewed_images={'app/src/main/res/drawable-nodpi/openpodsmax_icon.png': '99348eafd803bc80c94acb9161ac281f48f55b40042db45657dfbef505e737b7', 'docs/images/pixel-system-controls.png': '8dc7d21d6c1e032c0f2283c590afce3956b9a850924ce375199b2b146ba574c7', 'docs/images/pixel-playback.png': '35e35c7bd26f020b689f1f96b0a49808eab5301a249be2cc8ddf1e8a81510d8d'}
reviewed_images.update({'app/src/main/res/drawable-nodpi/airpods_max_hero.png': 'c90a9fc6c05c3247db4a7a7145aed3c6a4b36c741eb17cdc43c0bb3ef3356ee0', 'app/src/main/res/drawable-nodpi/airpods_max_midnight.png': '38aeb14486cce310787508f57c639aac960a72886d75b9af32bac375fe636173', 'app/src/main/res/drawable-nodpi/airpods_max_starlight.png': '43646db8d627c9ab0c3f8fee9c095ec47118d1df892b7fcb76a8efbb973c89ad', 'app/src/main/res/drawable-nodpi/airpods_max_blue.png': '6783ee3daad85ff249d7dc05e8cf4ad6a73d1c8f4de8360a6c25e9ec8a7f5838', 'app/src/main/res/drawable-nodpi/airpods_max_purple.png': 'b0adc85fb658fd57f6651ca0982ca2bb2f3f2b3c7b8f82d12ecf2933d11ee41a', 'app/src/main/res/drawable-nodpi/airpods_max_orange.png': '23fb73398415e8ccd3aec03cc406b650149fb0c75f511e01f562b72b587bcdb3', 'app/src/main/res/drawable-nodpi/airpods_crown_detail.png': '7fd132ddbee368fc9f6bce4c9686ad2b260dc5b3f28f214ab0b00f644656a091', 'app/src/main/res/drawable-nodpi/airpods_crown_detail_dark.png': '6d705375c985b6d391553211a0ce1d1ea1047681c25ad5dee62dc775d7bab303'})
failures=[]
for name in files:
 p=root/name
 if not p.is_file():continue
 if name in reviewed_images:
  if hashlib.sha256(p.read_bytes()).hexdigest()!=reviewed_images[name]:
   failures.append((name,0,'reviewed image changed; review required'))
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
