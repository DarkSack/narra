import test from 'node:test';
import assert from 'node:assert/strict';
import {removeObject} from '../src/platform.mjs';
test('limpieza idempotente: ausencia es éxito; un fallo real se conserva para reintento',async()=> {
  const client=status=>({storage:{from:()=>({remove:async()=>({error:status ? {statusCode:status}:null})})}});
  await removeObject(client(404),'books','missing.pdf');
  await removeObject(client(0),'books','book.pdf');
  await assert.rejects(removeObject(client(503),'books','book.pdf'));
});
