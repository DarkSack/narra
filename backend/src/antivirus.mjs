import net from 'node:net';
import { once } from 'node:events';
import { createReadStream } from 'node:fs';
import { PolicyError } from './policy.mjs';

async function command(write) {
  const socket = net.createConnection({ host:process.env.CLAMD_HOST ?? 'clamav', port:Number(process.env.CLAMD_PORT ?? 3310) });
  socket.setTimeout(120_000,()=>socket.destroy(new Error('Antivirus sin respuesta')));
  const response = new Promise((resolve,reject)=> {
    let reply = '';
    socket.on('data',chunk=> {
      reply += chunk.toString('utf8');
      if (reply.length > 4096) socket.destroy(new Error('Respuesta de antivirus no válida'));
      if (reply.includes('\0')) { resolve(reply.split('\0')[0]); socket.end(); }
    });
    socket.on('error',reject);
    socket.on('end',()=> { if (!reply.includes('\0')) reject(new Error('Análisis incompleto')); });
  });
  // Registrar el rechazo desde el inicio evita unhandled rejections durante el envío.
  response.catch(()=>{});
  try { await once(socket,'connect'); await write(socket); return await response; }
  finally { socket.destroy(); }
}
export function assertFresh(version, now=Date.now()) {
  // clamd VERSION usa la zona horaria del daemon; el despliegue fija UTC.
  const date = Date.parse(`${version.split('/').at(-1)} UTC`);
  if (!Number.isFinite(date) || now-date > 72*3600_000 || date-now > 3600_000) throw new Error('Las firmas del antivirus no están actualizadas');
}
export async function scan(file) {
  const version = await command(socket=>socket.write('zVERSION\0'));
  assertFresh(version);
  const verdict = await command(async socket=> {
    socket.write('zINSTREAM\0');
    for await (const chunk of createReadStream(file,{highWaterMark:64*1024})) {
      const size = Buffer.alloc(4); size.writeUInt32BE(chunk.length);
      socket.write(size);
      if (!socket.write(chunk)) await once(socket,'drain');
    }
    socket.write(Buffer.alloc(4));
  });
  if (verdict.endsWith(' FOUND')) throw new PolicyError('El antivirus detectó contenido peligroso');
  if (verdict !== 'stream: OK') throw new Error('El antivirus no completó el análisis');
}
