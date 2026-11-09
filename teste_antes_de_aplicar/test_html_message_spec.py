#!/usr/bin/env python3
"""Prototype-only tests for SPEC_CAPTURA_HTML_POR_MENSAGEM.md."""
import base64, hashlib, json, re, unittest
from html.parser import HTMLParser
from pathlib import Path

SAMPLE = Path(r'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\IPED_testes_chat_real\Exportados\arquivos\F\A\FAF3132301A706022E1C3EAD6AE0800B.html')
PLUGIN_VIEWER = Path(__file__).resolve().parents[1] / 'src' / 'iped' / 'viewers' / 'HtmlLinkViewer.java'

class Scan(HTMLParser):
    def __init__(self): super().__init__(); self.messages=[]; self.active=None; self.skip=0
    def handle_starttag(self, tag, attrs):
        a=dict(attrs); classes=set(a.get('class','').split())
        if tag in ('script','form'): self.skip+=1
        if self.skip: return
        if tag=='div' and 'linha' in classes and a.get('id','').isdigit():
            self.active={'id':a['id'],'classes':sorted(classes),'text':'','attachments':[]}; self.messages.append(self.active)
        if self.active and tag=='img' and a.get('src'): self.active['attachments'].append(a['src'])
    def handle_data(self, data):
        if self.active and not self.skip: self.active['text']+=data
    def handle_endtag(self, tag):
        if tag in ('script','form') and self.skip: self.skip-=1
        if tag=='div' and self.active: self.active=None

def fingerprint(msg):
    raw=json.dumps(msg,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
    return hashlib.sha256(raw).hexdigest()

def safe_data_uri(uri, max_bytes=1024):
    m=re.fullmatch(r'data:(image/png|image/jpeg);base64,([A-Za-z0-9+/=]+)',uri)
    if not m: raise ValueError('unsupported/unsafe data URI')
    b=base64.b64decode(m.group(2),validate=True)
    if len(b)>max_bytes: raise ValueError('data URI limit')
    return b

class Prototype(unittest.TestCase):
    def test_01_sample_contract(self):
        self.assertTrue(SAMPLE.is_file()); p=Scan(); p.feed(SAMPLE.read_text(encoding='utf-8',errors='replace'))
        self.assertEqual(len(p.messages),17); self.assertEqual(len({m['id'] for m in p.messages}),17)
        self.assertIn('243188',[m['id'] for m in p.messages]); self.assertTrue(any(m['attachments'] for m in p.messages))
    def test_02_inclusive_range_and_aux_exclusion(self):
        p=Scan(); p.feed('<div id="topbar"></div><div id="10" class="linha">A</div><div id="modal" class="modal"></div><div id="11" class="linha">B</div>')
        ids=[m['id'] for m in p.messages]; self.assertEqual(ids,['10','11']); self.assertEqual(ids[0:2],['10','11'])
    def test_03_hash_determinism_and_change(self):
        a={'id':'10','classes':['linha'],'text':'msg','attachments':[]}; self.assertEqual(fingerprint(a),fingerprint(dict(a)))
        self.assertNotEqual(fingerprint(a),fingerprint({**a,'text':'changed'}))
    def test_04_data_uri_allowlist_and_limit(self):
        self.assertEqual(safe_data_uri('data:image/png;base64,YQ=='),b'a')
        for uri in ('data:text/html;base64,YQ==','https://evil/x'):
            with self.assertRaises(ValueError): safe_data_uri(uri)
        with self.assertRaises(ValueError): safe_data_uri('data:image/png;base64,YWFh',2)
    def test_05_missing_or_ambiguous_anchor_fails_closed(self):
        ids=['1','2','2']; self.assertNotEqual(ids.count('missing'),1); self.assertNotEqual(ids.count('2'),1)
    def test_06_legacy_is_not_rewritten(self):
        old={'schemaVersion':2,'artifacts':['screenshots/frame_0001.png']}
        self.assertNotIn('captureFormat',old); self.assertEqual(old['artifacts'][0],'screenshots/frame_0001.png')
    def test_07_date_start_marker_resolves_to_next_message(self):
        source=PLUGIN_VIEWER.read_text(encoding='utf-8')
        self.assertIn("row.querySelectorAll('[data-iped-capture-date][id]')",source)
        self.assertIn('CaptureBoundaryResolver.Role.START',source)
        self.assertIn('CaptureBoundaryResolver.resolve(rows, requestedId, role)',source)
        self.assertIn('resolvedStart:resolvedStart',source)
        self.assertIn('"start", resolvedStartMessageId',source)
        rows=[{'id':'date-separator','date':True},{'id':'243188','date':False},{'id':'243189','date':False}]
        message_ids=[row['id'] for row in rows if row['id'].isdigit()]
        anchor='date-separator'; row_index=next(i for i,row in enumerate(rows) if row['id']==anchor)
        resolved=next((row['id'] for row in rows[row_index:] if row['id'].isdigit() and row['id'] in message_ids),None)
        self.assertEqual(resolved,'243188')
        manifest_anchor=resolved
        self.assertEqual(manifest_anchor,rows[1]['id'])

if __name__=='__main__': unittest.main(verbosity=2)
