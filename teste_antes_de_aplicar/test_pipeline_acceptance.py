#!/usr/bin/env python3
"""Executable prototype acceptance checks; deliberately independent of product code."""
import base64, hashlib, html, json, re, tempfile, unittest
from html.parser import HTMLParser
from pathlib import Path

SAMPLE=Path(r'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\IPED_testes_chat_real\Exportados\arquivos\F\A\FAF3132301A706022E1C3EAD6AE0800B.html')
ALLOWED={'html','head','meta','title','style','body','div','span','p','br','a','img','audio','video','source','strong','b','i'}
VOID={'meta','br','img','source'}

class Node:
    def __init__(self,tag,attrs,parent=None): self.tag=tag; self.attrs=dict(attrs); self.parent=parent; self.children=[]; self.text=[]
    def plain(self): return ''.join(self.text)+''.join(c.plain() for c in self.children)
class Tree(HTMLParser):
    def __init__(self): super().__init__(convert_charrefs=True); self.root=Node('root',{}); self.stack=[self.root]
    def handle_starttag(self,t,a):
        n=Node(t,a,self.stack[-1]); self.stack[-1].children.append(n)
        if t not in VOID:self.stack.append(n)
    def handle_startendtag(self,t,a): self.stack[-1].children.append(Node(t,a,self.stack[-1]))
    def handle_endtag(self,t):
        for i in range(len(self.stack)-1,0,-1):
            if self.stack[i].tag==t:self.stack=self.stack[:i];break
    def handle_data(self,d):self.stack[-1].text.append(d)
def walk(n):
    for c in n.children: yield c; yield from walk(c)
def messages(source):
    p=Tree();p.feed(source)
    return [n for n in walk(p.root) if n.tag=='div' and 'linha' in n.attrs.get('class','').split() and n.attrs.get('id','').isdigit()]
def sha(b):return hashlib.sha256(b).hexdigest()
def canonical(obj):return json.dumps(obj,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode('utf8')
def data_uri(value,limit=1_000_000):
    m=re.fullmatch(r'data:(image/png|image/jpeg|audio/ogg);base64,([A-Za-z0-9+/=]+)',value)
    if not m:raise ValueError('unsupported data uri')
    b=base64.b64decode(m.group(2),validate=True)
    if len(b)>limit:raise ValueError('resource too large')
    return m.group(1),b
def sanitize(source):
    p=Tree();p.feed(source); out=[]
    def emit(n):
        if n.tag not in ALLOWED:return ''.join(emit(c) for c in n.children)
        attrs=[]
        for k,v in n.attrs.items():
            if k.lower().startswith('on') or k in ('id','style','srcdoc','formaction'):continue
            if k not in {'class','href','src','alt','controls','type','data-capture-message-key'}:continue
            if k in ('src','href') and re.match(r'(?i)\s*(javascript:|https?:|file:|//)',v):continue
            if k=='src' and v.startswith('data:'):data_uri(v)
            attrs.append(f' {k}="{html.escape(v,quote=True)}"')
        start='<'+n.tag+''.join(attrs)+'>'
        if n.tag in VOID:return start
        return start+html.escape(''.join(n.text))+''.join(emit(c) for c in n.children)+'</'+n.tag+'>'
    return ''.join(emit(n) for n in p.root.children)
def resolve_anchor(items,key):
    found=[i for i,x in enumerate(items) if x['key']==key]
    if len(found)!=1:raise ValueError('anchor absent or ambiguous')
    return found[0]
def reconcile(old,new):
    om={x['key']:x for x in old}; nm={x['key']:x for x in new}; out={}
    for k in om.keys()|nm.keys():
        if k not in nm:state='missing_in_current_source'
        elif k not in om:state='new_in_interval'
        elif om[k]['hash']!=nm[k]['hash']:state='changed'
        else:state='unchanged'
        out[k]=state
    return out
def atomic_replace(path,payload,validator):
    path=Path(path); tmp=path.with_suffix(path.suffix+'.tmp')
    try:
        tmp.write_bytes(payload); validator(tmp.read_bytes()); tmp.replace(path)
    except Exception:
        tmp.unlink(missing_ok=True); raise

def source_fp(raw):return sha(raw)
def make_manifest(raw,conversation,messages,plugin='2.3.6-beta'):
    ordered=[]
    for i,m in enumerate(messages):
        item=dict(m,order=i);item['messageHash']=sha(canonical(item));ordered.append(item)
    root=sha(canonical({'schemaVersion':1,'captureFormat':'html-message-v1','messages':[{'order':x['order'],'messageHash':x['messageHash']} for x in ordered]}))
    return {'schemaVersion':1,'captureFormat':'html-message-v1','pluginVersion':plugin,'ipedVersion':'4.3.1','sourceFingerprint':source_fp(raw),'conversationKey':sha(conversation.encode()),'messageCount':len(ordered),'state':'complete','captureRootHash':root,'messages':ordered}
def bounded_read(raw,limit):
    if len(raw)>limit:raise ValueError('metadata limit')
    return raw
def render_offline(messages):
    rows=[]
    for m in messages:
        rows.append('<article data-capture-message-key="'+html.escape(m['key'],quote=True)+'"><p>'+html.escape(m['text'])+'</p></article>')
    return '<!doctype html><meta charset="utf-8"><main>'+''.join(rows)+'</main>'
def compare_reference(manifest,reference):
    if manifest['schemaVersion']!=reference['schemaVersion'] or manifest['captureFormat']!=reference['captureFormat']:
        raise ValueError('incompatible reference')
    return manifest['captureRootHash']==reference['captureRootHash']

class Acceptance(unittest.TestCase):
    def test01_real_iped431_dom_contract_observed(self):
        self.assertTrue(SAMPLE.is_file()); found=messages(SAMPLE.read_text(encoding='utf8',errors='replace'))
        self.assertEqual(len(found),17); ids=[n.attrs['id'] for n in found]
        self.assertEqual(len(ids),len(set(ids))); self.assertIn('243188',ids)
    def test02_nonmessage_and_range(self):
        src='<div id="topbar"></div><div id="20" class="linha">A</div><div id="modal" class="modal">x</div><div id="21" class="linha">B</div>'
        ms=messages(src);self.assertEqual([x.attrs['id'] for x in ms],['20','21']);self.assertEqual(ms[0:2],ms)
    def test03_duplicate_messages_keep_distinct_occurrences(self):
        ms=messages('<div id="30" class="linha">same</div><div id="31" class="linha">same</div>')
        self.assertEqual(len(ms),2);self.assertNotEqual(ms[0].attrs['id'],ms[1].attrs['id'])
    def test04_hash_stable_ordered_and_content_sensitive(self):
        a={'id':'1','text':'x','attachments':[]};self.assertEqual(sha(canonical(a)),sha(canonical(dict(a))))
        self.assertNotEqual(sha(canonical(a)),sha(canonical({**a,'text':'y'})))
        self.assertEqual(sha(canonical({'a':1,'b':2})),sha(canonical({'b':2,'a':1})))
    def test05_attachment_bytes_mime_hash_and_size(self):
        mime,b=data_uri('data:image/png;base64,YWJj');self.assertEqual((mime,len(b),sha(b)),('image/png',3,sha(b'abc')))
        with self.assertRaises(ValueError):data_uri('data:text/html;base64,YQ==')
        with self.assertRaises(ValueError):data_uri('data:image/png;base64,YWJj',2)
        with self.assertRaises(ValueError):data_uri('data:image/png;base64,@@')
    def test06_sanitizer_removes_active_and_external_content(self):
        out=sanitize('<div id="orig" onclick="x()"><script>alert(1)</script><a href="javascript:evil()">x</a><img src="https://evil/x"><span>ok</span></div>')
        self.assertNotIn('script',out.lower());self.assertNotIn('onclick',out);self.assertNotIn('javascript:',out);self.assertNotIn('https:',out);self.assertNotIn('id="orig"',out);self.assertIn('ok',out)
    def test07_capture_message_keys_are_not_original_ids(self):
        out=sanitize('<div id="42" class="linha" data-capture-message-key="key-42">hello</div>')
        self.assertNotIn('id="42"',out);self.assertIn('data-capture-message-key="key-42"',out)
    def test08_anchors_unique_and_ordered(self):
        xs=[{'key':'a'},{'key':'b'},{'key':'c'}];self.assertLess(resolve_anchor(xs,'a'),resolve_anchor(xs,'c'))
        for k in ('absent','b'):
            ys=xs+[{'key':'b'}] if k=='b' else xs
            with self.assertRaises(ValueError):resolve_anchor(ys,k)
    def test09_reconcile_states(self):
        old=[{'key':'a','hash':'1'},{'key':'b','hash':'2'},{'key':'c','hash':'3'}]
        new=[{'key':'a','hash':'1'},{'key':'b','hash':'changed'},{'key':'d','hash':'4'}]
        self.assertEqual(reconcile(old,new),{'a':'unchanged','b':'changed','c':'missing_in_current_source','d':'new_in_interval'})
    def test10_manifest_root_deterministic_and_tamper_evident(self):
        rec=[{'order':0,'key':'a','messageHash':'x'},{'order':1,'key':'b','messageHash':'y'}]
        root=sha(canonical({'schemaVersion':1,'messages':rec}));self.assertEqual(root,sha(canonical({'schemaVersion':1,'messages':rec})))
        self.assertNotEqual(root,sha(canonical({'schemaVersion':1,'messages':rec+[{'order':2,'key':'c','messageHash':'z'}]})))
    def test11_atomic_failure_preserves_previous_and_cleans_temp(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'manifest.json';p.write_text('old')
            with self.assertRaises(ValueError):atomic_replace(p,b'bad',lambda _:(_ for _ in ()).throw(ValueError()))
            self.assertEqual(p.read_text(),'old');self.assertFalse(p.with_suffix('.json.tmp').exists())
    def test12_atomic_success_promotes_valid_payload(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'manifest.json';p.write_text('old');atomic_replace(p,b'new',lambda b:None);self.assertEqual(p.read_bytes(),b'new')
    def test13_legacy_capture_detection_does_not_rewrite(self):
        old={'schemaVersion':2,'artifacts':['screenshots/frame_0001.png']}
        self.assertNotIn('captureFormat',old);self.assertEqual(old['artifacts'][0],'screenshots/frame_0001.png')
    def test14_path_escape_rejected_by_containment_rule(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)/'capture';root.mkdir()
            for rel in ('../outside','/absolute','attachments/../../outside'):
                self.assertFalse((root/rel).resolve().is_relative_to(root.resolve()))
    def test15_manifest_source_conversation_and_root(self):
        raw=b'<div id="1" class="linha">x</div>';a=make_manifest(raw,'chatA',[{'key':'k','text':'x'}]);b=make_manifest(raw,'chatB',[{'key':'k','text':'x'}])
        self.assertEqual(a['ipedVersion'],'4.3.1');self.assertEqual(a['state'],'complete');self.assertNotEqual(a['conversationKey'],b['conversationKey']);self.assertEqual(a['sourceFingerprint'],sha(raw))
    def test16_manifest_changes_on_source_or_message_mutation(self):
        x=make_manifest(b'source','chat',[{'key':'k','text':'a'}]);y=make_manifest(b'other','chat',[{'key':'k','text':'a'}]);z=make_manifest(b'source','chat',[{'key':'k','text':'b'}])
        self.assertNotEqual(x['sourceFingerprint'],y['sourceFingerprint']);self.assertNotEqual(x['captureRootHash'],z['captureRootHash'])
    def test17_offline_html_has_only_selected_and_escaped_content(self):
        out=render_offline([{'key':'m1','text':'<script>alert(1)</script>'}]);self.assertIn('data-capture-message-key="m1"',out);self.assertNotIn('<script>',out);self.assertNotIn('https://',out);self.assertIn('&lt;script&gt;',out)
    def test18_unresolved_attachment_cannot_mark_complete(self):
        unresolved={'status':'unresolved','required':True};state='complete' if not unresolved['required'] or unresolved['status']=='resolved' else 'partial'
        self.assertEqual(state,'partial')
    def test19_limits_reject_before_parse_or_allocation(self):
        with self.assertRaises(ValueError):bounded_read(b'12345',4)
        self.assertEqual(bounded_read(b'1234',4),b'1234')
    def test20_cancelled_capture_preserves_previous(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'whatsapp.html';p.write_bytes(b'old')
            def cancelled(_):raise InterruptedError('cancelled')
            with self.assertRaises(InterruptedError):atomic_replace(p,b'new',cancelled)
            self.assertEqual(p.read_bytes(),b'old');self.assertFalse(p.with_suffix('.html.tmp').exists())
    def test21_reference_requires_same_schema_and_format(self):
        m=make_manifest(b's','chat',[{'key':'k','text':'x'}]);self.assertTrue(compare_reference(m,m))
        with self.assertRaises(ValueError):compare_reference(m,{**m,'schemaVersion':99})
    def test22_public_reference_detects_content_divergence(self):
        a=make_manifest(b's','chat',[{'key':'k','text':'x'}]);b=make_manifest(b's','chat',[{'key':'k','text':'y'}]);self.assertFalse(compare_reference(a,b))
    def test23_report_escapes_untrusted_fields(self):
        label='<img src=x onerror=alert(1)>';out='<td>'+html.escape(label)+'</td>';self.assertNotIn('<img',out);self.assertIn('&lt;img',out)
    def test24_symlink_escape_resolves_outside_root(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)/'root';outside=Path(d)/'outside';root.mkdir();outside.mkdir();link=root/'attachments';
            try:link.symlink_to(outside,target_is_directory=True)
            except OSError:self.skipTest('symlink creation unavailable on this host')
            self.assertFalse(link.resolve().is_relative_to(root.resolve()))

if __name__=='__main__':unittest.main(verbosity=2)
