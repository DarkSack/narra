import yauzl from 'yauzl';
import { createWriteStream } from 'node:fs';
import { join } from 'node:path';
import { pipeline } from 'node:stream/promises';
import { Transform } from 'node:stream';
// El contenedor que interpreta PDFs no tiene secretos. Sus resultados también
// se tratan como no confiables: rutas, enlaces, cantidad y bytes acotados.
export function extractBundle(zip, directory) {
  return new Promise((resolve,reject)=> {
    yauzl.open(zip,{lazyEntries:true},(error,archive)=> {
      if (error) return reject(error);
      let total = 0, entries = 0;
      const names = new Set();
      const fail = error=> { archive.close(); reject(error); };
      archive.on('error',fail);
      archive.on('end',()=>resolve(names));
      archive.on('entry',entry=> {
        const name = entry.fileName;
        const mode = (entry.externalFileAttributes >>> 16) & 0xf000;
        if (!/^(book\.pdf|manifest\.json|page-[0-9]{1,3}\.(jpg|txt))$/.test(name) || names.has(name) ||
            mode && mode !== 0x8000 || ++entries > 1002 || entry.uncompressedSize > 100*1024*1024) return fail(new Error('Archivo de revisión no válido'));
        names.add(name);
        archive.openReadStream(entry,(error,stream)=> {
          if (error) return fail(error);
          const guard = new Transform({transform(chunk,_,done) {
            total += chunk.length;
            done(total > 250*1024*1024 ? new Error('Revisión demasiado grande') : null,chunk);
          }});
          pipeline(stream,guard,createWriteStream(join(directory,name),{flags:'wx',mode:0o600}))
            .then(()=>archive.readEntry(),fail);
        });
      });
      archive.readEntry();
    });
  });
}
