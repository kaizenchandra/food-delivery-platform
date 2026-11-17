/* No offline caching: private API data is never cached by this worker. */
self.addEventListener('install',()=>self.skipWaiting());
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
function safe(data){return data&&typeof data.id==='string'&&/^[a-f0-9-]{36}$/.test(data.id)&&typeof data.href==='string'&&/^\/(support|reviews|notifications|orders\/[a-f0-9-]{36})$/.test(data.href)}
async function display(data){
 if(!safe(data))throw new Error('Invalid push payload');
 await self.registration.showNotification('Saffron update',{body:'You have a new Saffron update. Sign in to view it.',tag:data.id,data:{id:data.id,href:data.href}});
 const pages=await self.clients.matchAll({type:'window',includeUncontrolled:true});for(const page of pages)page.postMessage({type:'SAFFRON_PUSH_DISPLAYED',id:data.id});
}
self.addEventListener('push',event=>{event.waitUntil((async()=>{const data=event.data?event.data.json():null;if(safe(data))await display(data)})())});
self.addEventListener('message',event=>{if(event.data?.type!=='LOCAL_PUSH'||!event.source?.url||new URL(event.source.url).origin!==self.location.origin)return;event.waitUntil((async()=>{try{await display(event.data.payload);event.ports[0]?.postMessage({shown:true})}catch{event.ports[0]?.postMessage({shown:false})}})())});
self.addEventListener('notificationclick',event=>{event.notification.close();const href=event.notification.data?.href;event.waitUntil(self.clients.openWindow(typeof href==='string'&&/^\/(support|reviews|notifications|orders\/[a-f0-9-]{36})$/.test(href)?href:'/notifications'))});
