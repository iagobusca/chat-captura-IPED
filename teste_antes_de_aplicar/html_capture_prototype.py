"""Isolated executable model of html-message-v1. Never used by production code."""
import base64, hashlib, html, json, re
from html.parser import HTMLParser
from pathlib import Path, PurePosixPath

ALLOW_TAGS={'html','head','meta','title','style','body','main','article','div','span','p','br','a','img','audio','video','source','b','strong','i'}
VOID={'meta','br','img','source'}
MAX_DATA=8*1024*1024
class Node:
    def __init__(self,tag,attrs=()): self.tag=tag;self.attrs=dict(attrs);self.children=[];self.parts=[]
    def text(self):return ''.join(self.parts)+''.join(x.text() for x in self.children)
class DOM(HTMLParser):
    def __init__(self):super().__init__(convert_charrefs=True);self.root=Node('root');self.stack=[self.root]
    def handle_starttag(self,t,a):
        n=Node(t,a);self.stack[-1].children.append(n)
        if t not in VOID:self.stack.append(n)
    def handle_startendtag(self,t,a):self.stack[-1].children.append(Node(t,a))
    def handle_endtag(self,t):
        for i in range(len(self.stack)-1,0,-1):
            if self.stack[i].tag==t:self.stack=self.stack[:i];break
    def handle_data(self,d):self.stack[-1].parts.append(d)
def walk(n):
    for c in n.children:yield c;yield from walk(c)
def sha(b):return hashlib.sha256(b).hexdigest()
def canonical(o):return json.dumps(o,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode('utf8')
def parse_data(uri):
    m=re.fullmatch(r'data:([\w.+-]+/[\w.+-]+);base64,([A-Za-z0-9+/=\r\n]+)',uri)
    if not m:raise ValueError('unsupported data URI')
    raw=base64.b64decode(m.group(2),validate=True)
    if len(raw)>MAX_DATA:raise ValueError('data URI too large')
    return m.group(1).lower(),raw
def is_message(n):
    # Contract observed for 4.3.1: numeric-id div.linha. Aux elements excluded by shape.
    return n.tag=='div' and 'linha' in n.attrs.get('class','').split() and n.attrs.get('id','').isdigit()
def candidates(source):
    p=DOM();p.feed(source)
    return [n for n in walk(p.root) if is_message(n)]
def attrs_safe(n,attachment_map):
    out=[]
    for k,v in n.attrs.items():
        k=k.lower()
        if k.startswith('on') or k in {'id','style','srcdoc','formaction'}:continue
        if k not in {'class','alt','controls','type','href','src','data-capture-message-key'}:continue
        if k in {'href','src'}:
            if re.match(r'(?i)^\s*(javascript:|https?:|file:|//)',v):continue
            if v.startswith('data:'):
                mime,data=parse_data(v);name=sha(data);attachment_map[name]={'mime':mime,'bytes':data,'sha256':name};v='attachments/'+name
            elif v and not v.startswith('#'):continue
        out.append((k,v))
    return out
def render_node(n,attachment_map,key=None):
    if n.tag not in ALLOW_TAGS:return ''.join(render_node(c,attachment_map) for c in n.children)
    attrs=attrs_safe(n,attachment_map)
    if key is not None:attrs.append(('data-capture-message-key',key))
    s='<'+n.tag+''.join(' '+k+'="'+html.escape(v,quote=True)+'"' for k,v in attrs)+'>'
    if n.tag in VOID:return s
    return s+html.escape(''.join(n.parts))+''.join(render_node(c,attachment_map) for c in n.children)+'</'+n.tag+'>'
def identity(source_fp,conversation,n,ordinal):
    # Snapshot fingerprint is provenance, not a stable message key: it changes when the conversation grows.
    natural={'conversation':conversation,'domId':n.attrs['id'],'text':' '.join(n.text().split()),'classes':sorted(n.attrs.get('class','').split()),'ordinal':ordinal}
    return sha(canonical(natural)),natural
def make_capture(source,conversation,start_id,end_id,source_name='source.html'):
    raw=source.encode('utf8');sf=sha(raw);ms=candidates(source);ids=[n.attrs['id'] for n in ms]
    if len(ids)!=len(set(ids)):raise ValueError('duplicate DOM ids')
    if ids.count(start_id)!=1 or ids.count(end_id)!=1:raise ValueError('missing/ambiguous anchor')
    a,b=ids.index(start_id),ids.index(end_id)
    if a>b:raise ValueError('anchors reversed')
    selected=ms[a:b+1];attachments={};records=[];fragments=[];dups={}
    for order,n in enumerate(selected):
        base='|'.join([n.attrs['id'],' '.join(n.text().split()),n.attrs.get('class','')]);ordinal=dups.get(base,0);dups[base]=ordinal+1
        key,natural=identity(sf,conversation,n,ordinal)
        msg={'key':key,'domId':n.attrs['id'],'order':order,'text':' '.join(n.text().split()),'classes':natural['classes'],'attachments':[]}
        # Extract embedded resources only from this selected message subtree.
        for x in walk(n):
            for attr in ('src','href'):
                val=x.attrs.get(attr,'')
                if val.startswith('data:'):
                    mime,data=parse_data(val);digest=sha(data);attachments[digest]={'mime':mime,'bytes':data,'sha256':digest};msg['attachments'].append({'path':'attachments/'+digest,'mime':mime,'size':len(data),'sha256':digest})
                elif attr=='src' and val and re.match(r'(?i)^(https?:|file:|//)',val):raise ValueError('external attachment unresolved')
        msg['messageHash']=sha(canonical(msg));records.append(msg);fragments.append(render_node(n,attachments,key))
    root=sha(canonical({'schemaVersion':1,'captureFormat':'html-message-v1','records':[{'key':m['key'],'hash':m['messageHash']} for m in records]}))
    manifest={'schemaVersion':1,'captureFormat':'html-message-v1','ipedVersion':'4.3.1','sourceFingerprint':sf,'sourceName':Path(source_name).name,'conversationKey':sha(conversation.encode()),'anchors':{'start':start_id,'end':end_id},'messageCount':len(records),'state':'complete','captureRootHash':root,'messagesFile':'messages.json'}
    page='<!doctype html><html><head><meta charset="utf-8"><title>Captura</title></head><body><main>'+''.join(fragments)+'</main></body></html>'
    return manifest,records,page,attachments
def write_capture(root,result):
    root=Path(root);root.mkdir(parents=True,exist_ok=True);manifest,records,page,attachments=result
    files={'manifest.json':canonical(manifest),'messages.json':canonical({'messages':records}),'whatsapp.html':page.encode()}
    for digest,a in attachments.items():files['attachments/'+digest]=a['bytes']
    staged=[]
    try:
        for rel,data in files.items():
            p=PurePosixPath(rel)
            if p.is_absolute() or '..' in p.parts:raise ValueError('unsafe output path')
            dst=root.joinpath(*p.parts);dst.parent.mkdir(parents=True,exist_ok=True);tmp=dst.with_name(dst.name+'.tmp');tmp.write_bytes(data);staged.append((tmp,dst))
        # Validate all staged bytes before promotion.
        for tmp,dst in staged:
            if not tmp.is_file():raise IOError('missing staged output')
        for tmp,dst in staged:tmp.replace(dst)
    except Exception:
        for tmp,dst in staged:tmp.unlink(missing_ok=True)
        raise
def verify_capture(root):
    root=Path(root);m=json.loads((root/'manifest.json').read_text(encoding='utf8'));r=json.loads((root/'messages.json').read_text(encoding='utf8'))['messages']
    if m.get('captureFormat')!='html-message-v1' or m.get('ipedVersion')!='4.3.1':raise ValueError('format/version mismatch')
    if len(r)!=m['messageCount']:raise ValueError('count mismatch')
    for x in r:
        for a in x['attachments']:
            p=(root/a['path']).resolve()
            if not p.is_relative_to(root.resolve()):raise ValueError('path escape')
            b=p.read_bytes()
            if len(b)!=a['size'] or sha(b)!=a['sha256']:raise ValueError('attachment mismatch')
    root_hash=sha(canonical({'schemaVersion':1,'captureFormat':'html-message-v1','records':[{'key':x['key'],'hash':x['messageHash']} for x in r]}))
    if root_hash!=m['captureRootHash']:raise ValueError('capture root mismatch')
    return True
