// Se ejecuta explícitamente en el worker de pruebas; usa ClamAV, OCR y moderación reales.
// Storage y Postgres se mantienen en memoria para no publicar ni conservar libros de prueba.
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
process.env.NODE_ENV='test';
const {processBook}=await import('/app/src/worker.mjs');
function fixture() {
  const text='BT /F1 22 Tf 72 700 Td (Un libro para escuchar) Tj 0 -40 Td /F1 12 Tf (Una familia viaja al bosque y disfruta de una tarde tranquila.) Tj ET';
  const objects=[
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${text.length} >>\nstream\n${text}\nendstream`,
  ];
  let pdf='%PDF-1.7\n';const offsets=[0];
  objects.forEach((object,index)=>{offsets.push(Buffer.byteLength(pdf));pdf+=`${index+1} 0 obj\n${object}\nendobj\n`;});
  const xref=Buffer.byteLength(pdf);
  pdf+=`xref\n0 6\n0000000000 65535 f \n${offsets.slice(1).map(offset=>String(offset).padStart(10,'0')+' 00000 n \n').join('')}trailer\n<< /Root 1 0 R /Size 6 >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(pdf);
}
const original=fixture();
const row={id:'00000000-0000-4000-8000-000000000009',title:'Un libro para escuchar',author:'Prueba de Narra',description:'Cuento familiar',status:'processing',source_key:'fixture/source.pdf',original_bytes:original.length};
const files=new Map([['quarantine/fixture/source.pdf',new Blob([original])]]);
const client={
  database:{from(){
    const filters=[];let change;
    const query={select(){return query},eq(k,v){filters.push([k,v]);return query},limit(){return query},update(value){change=value;return query},then(resolve){
      const matches=filters.every(([key,value])=>row[key]===value);
      if(matches && change) Object.assign(row,change);
      return Promise.resolve({data:matches?[{...row}]:[],error:null}).then(resolve);
    }};return query;
  }},
  storage:{from(bucket){return {
    async download(key){return {data:files.get(`${bucket}/${key}`),error:null}},
    async upload(key,blob){files.set(`${bucket}/${key}`,blob);return {data:{key},error:null}},
    async remove(key){files.delete(`${bucket}/${key}`);return {data:{},error:null}},
  }}},
};
await processBook(client,{...row});
assert.equal(row.status,'review',row.reason);
assert.equal(row.checks_passed,true);
assert.equal(row.pages,1);
assert.equal(row.source_key,null);
assert.equal(files.has('quarantine/fixture/source.pdf'),false);
const result=Buffer.from(await files.get(`books/${row.pdf_key}`).arrayBuffer());
assert.equal(createHash('sha256').update(result).digest('hex'),row.sha256);
assert.equal(result.length,row.pdf_bytes);
assert.equal(result.subarray(0,5).toString(),'%PDF-');
console.log(`PASS: antivirus + CDR/OCR + moderación de página real + integridad; pendiente de revisión humana (${result.length} bytes)`);
