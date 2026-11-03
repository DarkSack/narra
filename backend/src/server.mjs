import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { mkdtemp, rm } from 'node:fs/promises';
import { createWriteStream, openAsBlob } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { randomUUID, createHash, createHmac, timingSafeEqual } from 'node:crypto';
import { platform, unwrap, patchBook, removeObject, sha256, notifyReview, PUBLIC_COLUMNS } from './platform.mjs';
import { metadata, validateUploadHeaders, PdfStream, PolicyError, MAX_BYTES } from './policy.mjs';
import { reviewQueue, decide } from './review.mjs';

const MAX_TRANSFERS = 3;
const REPORTS_TO_HIDE = 3;
const REPORTS_PER_DAY = 10;
// Una cuenta recién creada denuncia, pero no cuenta para retirar un libro sin revisión humana.
const TRUSTED_ACCOUNT_AGE = 7 * 86400_000;
// Sin tope fijo de 180 s: una conexión lenta de verdad termina; una que gotea no retiene su hueco.
const MIN_TRANSFER_RATE = 64 * 1024;
const UPLOAD_TIME_LIMIT = 30 * 60_000;
export function watchRate(bytes, abort) {
  const started = Date.now();
  const timer = setInterval(() => {
    const seconds = (Date.now()-started)/1000;
    if (seconds > 60 && bytes() < seconds*MIN_TRANSFER_RATE) abort();
  }, 5000);
  return () => clearInterval(timer);
}
// Quien bloquea no necesita el id de la otra cuenta: basta un valor que solo sirve para desbloquear.
const blockToken = (userId, blockedId) => createHash('sha256').update(`${userId}:${blockedId}`).digest('hex').slice(0,32);

const digest = text => createHash('sha256').update(text).digest();
const same = (a, b) => timingSafeEqual(digest(a), digest(b));
const SESSION_TIME = 12 * 3600_000;
// La llave sale de la contraseña: cambiar ADMIN_PASSWORD cierra todas las sesiones abiertas.
const sign = expires => createHmac('sha256', `narra-admin:${process.env.ADMIN_PASSWORD}`).update(String(expires)).digest('base64url');
/** Panel de revisión: entra con ADMIN_PASSWORD y recibe una cookie de sesión firmada. Sin la variable, no existe. */
function adminPanel(client) {
  const admin = express.Router();
  admin.use((req, res, next) => {
    if (!process.env.ADMIN_PASSWORD) return res.sendStatus(404);
    // Además de SameSite=Strict: un formulario de otro sitio no puede poner esta cabecera.
    if (req.method !== 'GET' && req.get('X-Narra-Admin') !== '1') return res.sendStatus(403);
    next();
  });
  admin.use(express.static(join(dirname(fileURLToPath(import.meta.url)),'admin')));
  admin.post('/login', rateLimit({ windowMs: 15*60_000, limit: 10, skipSuccessfulRequests: true, message: { message: 'Demasiados intentos. Espera 15 minutos.' } }), express.json({ limit: '1kb' }), (req, res) => {
    if (!same(String(req.body?.password ?? ''), process.env.ADMIN_PASSWORD)) return res.status(401).json({ message: 'Contraseña incorrecta' });
    const expires = Date.now() + SESSION_TIME;
    res.cookie('narra_admin', `${expires}.${sign(expires)}`, { httpOnly: true, secure: true, sameSite: 'strict', path: '/admin', maxAge: SESSION_TIME });
    res.sendStatus(204);
  });
  admin.post('/logout', (_req, res) => { res.clearCookie('narra_admin', { httpOnly: true, secure: true, sameSite: 'strict', path: '/admin' }); res.sendStatus(204); });
  admin.use((req, res, next) => {
    const [, expires, mac] = /(?:^|;\s*)narra_admin=(\d+)\.([\w-]+)/.exec(req.headers.cookie ?? '') ?? [];
    if (!(Number(expires) > Date.now()) || !same(mac, sign(expires))) return res.status(401).json({ message: 'Tu sesión terminó. Vuelve a entrar.' });
    next();
  });
  admin.get('/queue', async (_req, res) => res.json(await reviewQueue(client)));
  admin.get('/books/:id/pdf', async (req, res) => {
    if (!/^[a-f0-9-]{36}$/i.test(req.params.id)) return res.sendStatus(404);
    const [book] = unwrap(await client.database.from('catalog_books').select('pdf_key').eq('id',req.params.id).in('status',['review','published']).limit(1));
    if (!book?.pdf_key) return res.sendStatus(404);
    const blob = unwrap(await client.storage.from('books').download(book.pdf_key));
    res.set({'Content-Type':'application/pdf','Content-Length':String(blob.size),'Content-Disposition':`inline; filename="${req.params.id}.pdf"`});
    // La CSP de helmet (object-src 'none') puede dejar en blanco el visor de PDF de Chrome; nosniff sigue.
    res.removeHeader('Content-Security-Policy');
    await pipeline(Readable.fromWeb(blob.stream()), res);
  });
  admin.post('/books/:id/:action', async (req, res) => { await decide(client, req.params.action, req.params.id); res.sendStatus(204); });
  return admin;
}

export function createApp(client = platform()) {
  const app = express();
  app.disable('x-powered-by');
  // Solo Caddy llega a la API (compose publica 8080 en 127.0.0.1): su X-Forwarded-For da la IP real.
  app.set('trust proxy', 1);
  app.use(helmet());
  app.use((_req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });
  app.get('/health', (_req, res) => res.json({ ok: true }));
  app.use(rateLimit({ windowMs: 60_000, limit: 120 }));
  app.use('/admin', adminPanel(client));
  app.use(async (req, res, next) => {
    const token = req.headers.authorization;
    if (!/^Bearer [A-Za-z0-9._-]+$/.test(token ?? '')) return res.sendStatus(401);
    try {
      const response = await fetch(`${process.env.INSFORGE_URL}/api/auth/sessions/current`, {
        headers: { Authorization: token }, signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) return res.sendStatus(response.status === 401 ? 401 : 503);
      const { user } = await response.json();
      if (!user?.id || user.role && user.role !== 'authenticated') return res.sendStatus(403);
      req.userId = user.id; req.userCreatedAt = Date.parse(user.createdAt); next();
    } catch { res.sendStatus(503); }
  });
  const userLimits = rateLimit({ windowMs: 60_000, limit: 60, keyGenerator: req => req.userId });
  app.use(userLimits);
  // Acotar memoria y ancho de banda: pocas transferencias a la vez y una por cuenta.
  const transfers=new Set();
  const transfer=handler=>async(req,res,next)=> {
    if(transfers.has(req.userId)) return res.status(429).json({message:'Espera a que termine tu otra transferencia.'});
    if(transfers.size>=MAX_TRANSFERS) return res.status(503).json({message:'Hay muchas transferencias en curso. Intenta de nuevo en un momento.'});
    transfers.add(req.userId);
    const completed=new Promise(resolve=>{res.once('finish',resolve);res.once('close',resolve);});
    try { await handler(req,res); }
    catch(error) { next(error); }
    finally { await completed; transfers.delete(req.userId); }
  };
  app.get('/books', async (req, res) => {
    const offset = Math.max(0, Math.min(100000, Number(req.query.offset) || 0));
    let query = client.database.from('catalog_books').select(`${PUBLIC_COLUMNS},owner_id`).eq('status','published');
    const blocks=unwrap(await client.database.from('catalog_blocks').select('blocked_id').eq('user_id',req.userId).limit(1000));
    if(blocks.length) query=query.not('owner_id','in',`(${blocks.map(b=>b.blocked_id).join(',')})`);
    if (typeof req.query.q === 'string' && req.query.q.trim()) {
      const q = req.query.q.trim().slice(0,100).replace(/[%_*(),.]/g,'');
      query = query.ilike('title', `%${q}%`);
    }
    const rows = unwrap(await query.order('created_at', { ascending:false }).order('id').range(offset, offset+29));
    // El propietario no se expone: solo si el libro es tuyo, para no ofrecerte denunciarlo.
    res.json(rows.map(({ owner_id, ...book }) => ({ ...book, mine: owner_id === req.userId })));
  });
  app.get('/me/books', async (req,res) => {
    res.json(unwrap(await client.database.from('catalog_books').select(PUBLIC_COLUMNS).eq('owner_id',req.userId).order('created_at',{ascending:false}).limit(100)));
  });
  app.post('/books', rateLimit({ windowMs:3600_000, limit:10, keyGenerator:req=>req.userId }), transfer(async (req,res) => {
    validateUploadHeaders(req.headers['content-type'], req.query.filename);
    const meta = metadata(req.query);
    if (Number(req.headers['content-length']) > MAX_BYTES) throw new PolicyError('El PDF supera 100 MB',413);
    const id = unwrap(await client.database.rpc('reserve_catalog_upload', { p_owner:req.userId,p_title:meta.title,p_author:meta.author,p_description:meta.description }));
    const work = await mkdtemp(join(tmpdir(),'narra-upload-'));
    const key = `${id}/source.pdf`;
    try {
      const file = join(work,'source.pdf');
      const guard = new PdfStream();
      const stop = watchRate(() => guard.bytes, () => req.destroy(new PolicyError('La conexión es demasiado lenta para subir el PDF',408)));
      try { await pipeline(req, guard, createWriteStream(file, { mode:0o600 })); }
      finally { stop(); }
      // Se lee del disco al subir: el PDF nunca está entero en memoria.
      unwrap(await client.storage.from('quarantine').upload(key, await openAsBlob(file,{type:'application/pdf'})));
      await patchBook(client,id,{ status:'queued',source_key:key,original_bytes:guard.bytes },'uploading');
      res.status(202).json({ id, status:'queued' });
    } catch (e) {
      await removeObject(client,'quarantine',key).catch(()=>{});
      await patchBook(client,id,{status:'blocked',reason:'No se completó la subida'}).catch(()=>{});
      throw e;
    } finally { await rm(work,{recursive:true,force:true}); }
  }));
  app.get('/books/:id/pdf', transfer(async (req,res) => {
    const { id } = req.params;
    if (!/^[a-f0-9-]{36}$/i.test(id)) return res.sendStatus(404);
    const rows = unwrap(await client.database.from('catalog_books').select('pdf_key,sha256,pdf_bytes,owner_id').eq('id',id).eq('status','published').limit(1));
    if (!rows.length) return res.sendStatus(404);
    const book = rows[0];
    const blocks=unwrap(await client.database.from('catalog_blocks').select('blocked_id').eq('user_id',req.userId).eq('blocked_id',book.owner_id).limit(1));
    if(blocks.length) return res.sendStatus(404);
    const blob = unwrap(await client.storage.from('books').download(book.pdf_key));
    if (blob.size !== book.pdf_bytes || await sha256(blob) !== book.sha256) throw new Error('Integridad del PDF incorrecta');
    // Comprobar otra vez: una denuncia o baja concurrente revoca descargas nuevas.
    const live = unwrap(await client.database.from('catalog_books').select('id').eq('id',id).eq('status','published').limit(1));
    if (!live.length) return res.sendStatus(404);
    res.set({'Content-Type':'application/pdf','Content-Length':String(blob.size),'Content-Disposition':`attachment; filename="${id}.pdf"`,'X-PDF-SHA256':book.sha256});
    let sent = 0;
    const meter = new Transform({ transform(chunk,_,done) { sent += chunk.length; done(null,chunk); } });
    const stop = watchRate(() => sent, () => res.destroy());
    try { await pipeline(Readable.fromWeb(blob.stream()), meter, res); }
    finally { stop(); }
  }));
  app.post('/books/:id/report', express.json({limit:'8kb'}), async (req,res) => {
    const reason = req.body?.reason;
    if (typeof reason !== 'string' || reason.trim().length < 3 || reason.length > 1000) throw new PolicyError('Describe el motivo de la denuncia');
    const [book] = unwrap(await client.database.from('catalog_books').select('id,title,owner_id,reviewed_at').eq('id',req.params.id).eq('status','published').limit(1));
    if (!book) return res.sendStatus(404);
    if (book.owner_id===req.userId) throw new PolicyError('Para quitar tu libro, retíralo desde Mis publicaciones');
    const reports = () => client.database.from('catalog_reports');
    if (unwrap(await reports().select('id').eq('book_id',book.id).eq('reporter_id',req.userId).limit(1)).length) return res.sendStatus(204);
    const today = unwrap(await reports().select('id').eq('reporter_id',req.userId).gt('created_at',new Date(Date.now()-86400_000).toISOString()).limit(REPORTS_PER_DAY));
    if (today.length>=REPORTS_PER_DAY) throw new PolicyError('Has enviado muchas denuncias hoy. Inténtalo mañana',429);
    const trusted = Date.now()-req.userCreatedAt >= TRUSTED_ACCOUNT_AGE; // fecha ausente: NaN, no cuenta
    unwrap(await reports().insert([{book_id:book.id,reporter_id:req.userId,reason:reason.trim(),trusted}]));
    // Una sola cuenta no retira un libro: hacen falta varias cuentas con antigüedad desde su última aprobación.
    const recent = unwrap(await reports().select('id').eq('book_id',book.id).eq('trusted',true).gt('created_at',book.reviewed_at).limit(REPORTS_TO_HIDE));
    if (recent.length>=REPORTS_TO_HIDE && (await patchBook(client,book.id,{status:'review',reason:'Pendiente de revisar varias denuncias',reviewed_at:null},'published')).length) {
      await notifyReview(book,'se retiró por varias denuncias');
    }
    res.sendStatus(204);
  });
  app.post('/books/:id/block', async (req,res) => {
    const [book]=unwrap(await client.database.from('catalog_books').select('owner_id,title').eq('id',req.params.id).eq('status','published').limit(1));
    if(!book) return res.sendStatus(404);
    if(book.owner_id===req.userId) throw new PolicyError('No puedes bloquear tu propia cuenta');
    const blocks=unwrap(await client.database.from('catalog_blocks').select('blocked_id').eq('user_id',req.userId).limit(1000));
    if(blocks.length>=1000) throw new PolicyError('Has alcanzado el límite de bloqueos');
    if(!blocks.some(b=>b.blocked_id===book.owner_id)) unwrap(await client.database.from('catalog_blocks').insert([{user_id:req.userId,blocked_id:book.owner_id,label:book.title}]));
    res.sendStatus(204);
  });
  app.get('/me/blocks', async (req,res)=> {
    const rows = unwrap(await client.database.from('catalog_blocks').select('blocked_id,label').eq('user_id',req.userId).limit(1000));
    res.json(rows.map(b=>({id:blockToken(req.userId,b.blocked_id),label:b.label})));
  });
  app.delete('/me/blocks/:id', async (req,res)=> {
    const rows = unwrap(await client.database.from('catalog_blocks').select('blocked_id').eq('user_id',req.userId).limit(1000));
    const block = rows.find(b=>blockToken(req.userId,b.blocked_id)===req.params.id);
    if (block) unwrap(await client.database.from('catalog_blocks').delete().eq('user_id',req.userId).eq('blocked_id',block.blocked_id));
    res.sendStatus(204);
  });
  app.delete('/me/books/:id', async (req,res) => {
    const rows = unwrap(await client.database.from('catalog_books').select('id,source_key,pdf_key,status').eq('id',req.params.id).eq('owner_id',req.userId).limit(1));
    if (!rows.length) return res.sendStatus(404);
    const row = rows[0];
    await patchBook(client,row.id,{status:'blocked',reason:'Retirado por su autor'});
    for (const [bucket,key] of [['quarantine',row.source_key],['books',row.pdf_key]]) if (key) await removeObject(client,bucket,key);
    await patchBook(client,row.id,{source_key:null,pdf_key:null,checks_passed:false});
    res.sendStatus(204);
  });
  app.delete('/me', async (req,res) => {
    // Revocar primero: si falla el borrado remoto nunca se mantiene publicado.
    unwrap(await client.database.from('catalog_books').update({status:'blocked',reason:'Eliminando cuenta'}).eq('owner_id',req.userId));
    // Por páginas y solo las filas con archivos: los rechazados y retirados ya no los tienen.
    for (let offset=0;;offset+=100) {
      const rows = unwrap(await client.database.from('catalog_books').select('source_key,pdf_key').eq('owner_id',req.userId)
        .or('source_key.not.is.null,pdf_key.not.is.null').order('id').range(offset,offset+99));
      for (const row of rows) {
        for (const [bucket,key] of [['quarantine',row.source_key],['books',row.pdf_key]]) if(key) await removeObject(client,bucket,key);
      }
      if (rows.length<100) break;
    }
    const response = await fetch(`${process.env.INSFORGE_URL}/api/auth/users`,{
      method:'DELETE',headers:{Authorization:`Bearer ${process.env.INSFORGE_API_KEY}`,'Content-Type':'application/json'},
      body:JSON.stringify({userIds:[req.userId]}),signal:AbortSignal.timeout(15000),
    });
    if(!response.ok) throw new Error('No se pudo completar la eliminación de cuenta');
    res.sendStatus(204);
  });
  app.use((error,_req,res,_next)=> {
    const code = randomUUID();
    console.error('catalog_request_failed',code,error.status ?? 503,error.name); // Sin tokens, libros ni credenciales.
    if (!res.headersSent) res.status(error.status ?? 503).json({message:error instanceof PolicyError ? error.message : 'No se pudo completar la operación. Intenta más tarde.', reference:code});
  });
  return app;
}
if (process.env.NODE_ENV !== 'test') {
  const server = createApp().listen(Number(process.env.PORT ?? 8080),'0.0.0.0');
  server.requestTimeout = UPLOAD_TIME_LIMIT; // la subida lenta se corta por velocidad, no por reloj
  server.headersTimeout = 15_000;
  server.timeout = 180_000;
}
