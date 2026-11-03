import test from 'node:test';
import assert from 'node:assert/strict';
import { Readable,Writable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { PdfStream,metadata,validateUploadHeaders,validateVerdict,MAX_BYTES } from '../src/policy.mjs';
import { assertFresh } from '../src/antivirus.mjs';

test('firma PDF fragmentada y bytes reales, sin confiar en extensión',async()=> {
  const output=[]; const guard=new PdfStream();
  await pipeline(Readable.from([Buffer.from('%P'),Buffer.from('DF-1.7\n')]),guard,new Writable({write(c,_,done){output.push(c);done();}}));
  assert.equal(Buffer.concat(output).toString(),'%PDF-1.7\n');
  assert.equal(guard.bytes,9);
  for(const body of ['MZmalware','', '%PD']) {
    await assert.rejects(pipeline(Readable.from([Buffer.from(body)]),new PdfStream(),new Writable({write(_c,_e,cb){cb();}})));
  }
});
test('límite de subida aun sin Content-Length',async()=> {
  const guard=new PdfStream();guard.bytes=MAX_BYTES-4;
  await assert.rejects(pipeline(Readable.from([Buffer.from('%PDF-')]),guard,new Writable({write(_c,_e,cb){cb();}})),/100 MB/);
});
test('solo PDF; sin rutas, extensiones dobles ni consentimiento implícito',()=> {
  validateUploadHeaders('application/pdf','Libro.PDF');
  for(const name of ['book.pdf.exe','../book.pdf','folder/book.pdf','x:book.pdf','book.pdf\0']) assert.throws(()=>validateUploadHeaders('application/pdf',name));
  assert.throws(()=>validateUploadHeaders('application/zip','book.pdf'));
  assert.throws(()=>metadata({title:'Libro'}));
  assert.throws(()=>metadata({title:['Libro'],rights:'true',consent:'true'}));
  assert.equal(metadata({title:' Libro ',rights:'true',consent:'true'}).title,'Libro');
});
test('moderación falla cerrada ante respuestas incompletas, ambiguas o rechazadas',()=> {
  validateVerdict({obscene:false,uncertain:false});
  for(const verdict of [null,{}, {obscene:false}, {obscene:false,uncertain:'false'}, {obscene:true,uncertain:false},{obscene:false,uncertain:true},{obscene:false,uncertain:false,injected:true}]) assert.throws(()=>validateVerdict(verdict));
});
test('no usar firmas caducadas, fechas futuras ni respuestas de error',()=> {
  const now=Date.parse('2026-10-30T12:00:00Z');
  assertFresh('ClamAV 1.4.3/123/Fri Oct 30 09:00:00 2026',now);
  for(const version of ['ERROR','ClamAV 1/123/Oct 20 00:00:00 2026','ClamAV 1/123/Nov 20 00:00:00 2026']) assert.throws(()=>assertFresh(version,now));
});
