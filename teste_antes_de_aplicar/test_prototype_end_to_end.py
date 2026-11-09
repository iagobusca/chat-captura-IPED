import json,tempfile,unittest
from pathlib import Path
from html_capture_prototype import candidates,make_capture,write_capture,verify_capture

SAMPLE=Path(r'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\IPED_testes_chat_real\Exportados\arquivos\F\A\FAF3132301A706022E1C3EAD6AE0800B.html')
FIXTURE='''<html><body><div id="topbar">excluded</div><div id="100" class="linha incoming"><span class="name">Ana</span><span class="time">10:00</span><p>primeira</p><img src="data:image/png;base64,YWJj"></div><div id="date" class="date">hoje</div><div id="101" class="linha outgoing"><p>segunda &amp; texto</p><a href="javascript:evil()">unsafe</a></div><div id="102" class="linha incoming">fora do range</div></body></html>'''

class EndToEnd(unittest.TestCase):
    def test_capture_exact_range_excludes_aux_and_outside(self):
        m,records,page,att=make_capture(FIXTURE,'case/chat','100','101')
        self.assertEqual(m['messageCount'],2);self.assertNotIn('fora do range',page);self.assertNotIn('excluded',page)
        self.assertEqual(len(records[0]['attachments']),1);self.assertIn('attachments/',page)
        self.assertNotIn('javascript:',page);self.assertIn('data-capture-message-key',page)
    def test_capture_real_iped431_sample_interval(self):
        self.assertTrue(SAMPLE.is_file());src=SAMPLE.read_text(encoding='utf8',errors='replace');ids=[n.attrs['id'] for n in candidates(src)]
        result=make_capture(src,'real-chat',ids[0],ids[2]);self.assertEqual(result[0]['messageCount'],3);self.assertEqual(result[0]['ipedVersion'],'4.3.1')
    def test_new_messages_outside_range_keep_old_keys_stable(self):
        old=make_capture(FIXTURE,'case/chat','100','101')
        grown=FIXTURE.replace('<div id="topbar">excluded</div>','<div id="topbar">excluded</div><div id="99" class="linha">added before</div>').replace('<div id="102" class="linha">fora do range</div>','<div id="102" class="linha">fora do range</div><div id="103" class="linha">added after</div>')
        new=make_capture(grown,'case/chat','100','101')
        self.assertNotEqual(old[0]['sourceFingerprint'],new[0]['sourceFingerprint'])
        self.assertEqual([x['key'] for x in old[1]],[x['key'] for x in new[1]])
        self.assertEqual([x['messageHash'] for x in old[1]],[x['messageHash'] for x in new[1]])
    def test_missing_duplicate_reversed_anchors_fail_closed(self):
        src='<div id="1" class="linha">a</div><div id="2" class="linha">b</div><div id="2" class="linha">c</div>'
        with self.assertRaises(ValueError):make_capture(src,'c','1','2')
        with self.assertRaises(ValueError):make_capture(src,'c','missing','1')
        with self.assertRaises(ValueError):make_capture(src,'c','2','1')
    def test_roundtrip_verify_and_detect_attachment_tamper(self):
        with tempfile.TemporaryDirectory() as d:
            result=make_capture(FIXTURE,'c','100','100');write_capture(d,result);self.assertTrue(verify_capture(d))
            manifest=json.loads((Path(d)/'messages.json').read_text());p=Path(d)/manifest['messages'][0]['attachments'][0]['path'];p.write_bytes(b'evil')
            with self.assertRaises(ValueError):verify_capture(d)
    def test_format_and_count_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            write_capture(d,make_capture(FIXTURE,'c','100','100'));p=Path(d)/'manifest.json';m=json.loads(p.read_text());m['captureFormat']='legacy';p.write_text(json.dumps(m))
            with self.assertRaises(ValueError):verify_capture(d)

if __name__=='__main__':unittest.main(verbosity=2)
